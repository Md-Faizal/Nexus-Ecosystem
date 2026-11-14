const fs = require('fs');
const path = require('path');
const assert = require('assert');

// Test the rules configuration and exclusions logic
const config = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf-8'));
const exclusions = JSON.parse(fs.readFileSync(path.join(__dirname, 'exclusions.json'), 'utf-8'));

console.log('--- Verifying Nexus Fleet Responder Config & Rules ---');

// 1. Verify required files exist
const htmlTpl = fs.readFileSync(path.join(__dirname, config.paths.html_template), 'utf-8');
const txtTpl = fs.readFileSync(path.join(__dirname, config.paths.txt_template), 'utf-8');
const gifAsset = path.join(__dirname, 'assets', 'cursive_apple_git.gif');
const logoAsset = path.join(__dirname, 'assets', 'fz_logo.png');

assert(htmlTpl.length > 500, 'HTML template should have content');
assert(txtTpl.length > 50, 'TXT template should have content');
assert(fs.existsSync(gifAsset), 'cursive_apple_git.gif must exist');
assert(fs.existsSync(logoAsset), 'fz_logo.png must exist');
console.log('✓ Assets & templates exist and verified.');

// 2. Logic simulation
function isSuppressed(msg, senderAddr, db) {
  const senderLower = senderAddr.toLowerCase();
  const domain = senderLower.includes('@') ? senderLower.split('@')[1] : '';

  if (senderLower === config.mail.sender_email.toLowerCase()) {
    return [true, 'Self sender'];
  }

  for (const whitelisted of exclusions.whitelist_senders || []) {
    if (senderLower === whitelisted.toLowerCase()) {
      return [true, 'Sender is whitelisted contact (bypassed)'];
    }
  }

  const blSenders = (exclusions.blacklist_senders || []).map(s => s.toLowerCase());
  if (blSenders.includes(senderLower)) {
    return [true, 'Sender is blacklisted'];
  }

  const blDomains = (exclusions.blacklist_domains || []).map(d => d.toLowerCase());
  if (blDomains.includes(domain)) {
    return [true, `Domain ${domain} is blacklisted`];
  }

  for (const pattern of exclusions.drop_sender_patterns || []) {
    if (senderLower.includes(pattern.toLowerCase())) {
      return [true, `Sender matches automated pattern: ${pattern}`];
    }
  }

  const autoSubmitted = (msg['auto-submitted'] || '').toLowerCase();
  if (autoSubmitted && autoSubmitted !== 'no') {
    return [true, `Auto-Submitted header present: ${autoSubmitted}`];
  }

  const precedence = (msg['precedence'] || '').toLowerCase();
  if (['bulk', 'list', 'junk', 'auto_reply'].includes(precedence)) {
    return [true, `Precedence header is ${precedence}`];
  }

  if (msg['list-unsubscribe'] || msg['list-id']) {
    return [true, 'Mailing list header detected'];
  }

  if ((msg['x-autoreply'] || '').toLowerCase() === 'yes') {
    return [true, 'X-Autoreply detected'];
  }

  if (config.rules.ignore_threads_with_in_reply_to && msg['in-reply-to']) {
    return [true, 'Message is an existing reply thread (In-Reply-To exists)'];
  }

  if (db[senderLower]) {
    const lastReplied = db[senderLower];
    const diffDays = (Date.now() - lastReplied) / (1000 * 60 * 60 * 24);
    if (diffDays < config.rules.cooldown_days) {
      return [true, `Within ${config.rules.cooldown_days}-day cooldown`];
    }
  }

  return [false, 'Approved'];
}

// Tests
const mockDb = {};

// Test 1: Normal sender
let [suppressed, reason] = isSuppressed({}, 'collaborator@studio.org', mockDb);
assert.strictEqual(suppressed, false);
console.log('✓ Normal incoming sender -> Approved');

// Test 2: Self sender
[suppressed, reason] = isSuppressed({}, 'me@faizal.uk', mockDb);
assert.strictEqual(suppressed, true);
console.log('✓ Self sender -> Suppressed');

// Test 3: Whitelist
[suppressed, reason] = isSuppressed({}, 'example-friend@gmail.com', mockDb);
assert.strictEqual(suppressed, true);
console.log('✓ Whitelisted friend -> Suppressed (silent pass)');

// Test 4: Blacklisted domain
[suppressed, reason] = isSuppressed({}, 'receipts@stripe.com', mockDb);
assert.strictEqual(suppressed, true);
console.log('✓ Blacklist domain -> Suppressed');

// Test 5: Pattern matching
[suppressed, reason] = isSuppressed({}, 'no-reply@service.com', mockDb);
assert.strictEqual(suppressed, true);
console.log('✓ Pattern match (no-reply) -> Suppressed');

// Test 6: RFC 3834 loop protection
[suppressed, reason] = isSuppressed({'auto-submitted': 'auto-replied'}, 'other-bot@mail.net', mockDb);
assert.strictEqual(suppressed, true);
console.log('✓ Auto-Submitted header -> Suppressed');

// Test 7: Precedence bulk
[suppressed, reason] = isSuppressed({'precedence': 'bulk'}, 'marketing@promo.io', mockDb);
assert.strictEqual(suppressed, true);
console.log('✓ Precedence bulk -> Suppressed');

// Test 8: List-Unsubscribe
[suppressed, reason] = isSuppressed({'list-unsubscribe': '<mailto:unsub@list.com>'}, 'newsletter@tech.com', mockDb);
assert.strictEqual(suppressed, true);
console.log('✓ List-Unsubscribe -> Suppressed');

// Test 9: In-Reply-To ongoing thread
[suppressed, reason] = isSuppressed({'in-reply-to': '<prev-1234@faizal.uk>'}, 'client@design.com', mockDb);
assert.strictEqual(suppressed, true);
console.log('✓ In-Reply-To thread continuation -> Suppressed');

// Test 10: Cooldown logic
const testSender = 'prospect@newbiz.com';
[suppressed, reason] = isSuppressed({}, testSender, mockDb);
assert.strictEqual(suppressed, false);
mockDb[testSender] = Date.now(); // replied now

[suppressed, reason] = isSuppressed({}, testSender, mockDb);
assert.strictEqual(suppressed, true);
console.log('✓ Immediate follow-up from same sender -> Suppressed (Cooldown)');

// Simulate 8 days later
mockDb[testSender] = Date.now() - (8 * 24 * 60 * 60 * 1000);
[suppressed, reason] = isSuppressed({}, testSender, mockDb);
assert.strictEqual(suppressed, false);
console.log('✓ Sender after 8 days -> Approved (Cooldown expired)');

console.log('\nAll 10 test verification cases PASSED successfully!');
