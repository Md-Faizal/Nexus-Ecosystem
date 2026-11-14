use argon2::{
    password_hash::{rand_core::OsRng, PasswordHash, PasswordHasher, PasswordVerifier, SaltString},
    Argon2,
};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use rand::Rng;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TokenClaims {
    pub sub: String,              // User ID (e.g. 'usr_faizal')
    pub email: String,            // User Email
    pub name: String,             // Display Name
    pub roles: Vec<String>,       // e.g. ["owner", "admin"]
    pub token_version: i64,       // Version counter for global revocation
    pub device_id: Option<String>,
    pub exp: usize,               // Expiration timestamp (seconds since epoch)
    pub iat: usize,               // Issued-at timestamp
    pub jti: String,              // Unique token identifier UUID
}

/// Hash a plaintext password with Argon2id and random cryptographic salt
pub fn hash_password(password: &str) -> Result<String, String> {
    let salt = SaltString::generate(&mut OsRng);
    let argon2 = Argon2::default();
    argon2
        .hash_password(password.as_bytes(), &salt)
        .map(|h| h.to_string())
        .map_err(|e| format!("Password hashing failed: {}", e))
}

/// Constant-time verification of a plaintext password against an Argon2id hash
pub fn verify_password(password: &str, password_hash: &str) -> bool {
    let parsed_hash = match PasswordHash::new(password_hash) {
        Ok(h) => h,
        Err(_) => return false,
    };
    Argon2::default()
        .verify_password(password.as_bytes(), &parsed_hash)
        .is_ok()
}

/// Normalize and hash a security question answer
pub fn hash_security_answer(answer: &str) -> Result<String, String> {
    let normalized = answer.trim().to_lowercase();
    hash_password(&normalized)
}

/// Verify a security question answer case-insensitively
pub fn verify_security_answer(answer: &str, answer_hash: &str) -> bool {
    let normalized = answer.trim().to_lowercase();
    verify_password(&normalized, answer_hash)
}

/// Generate a cryptographically secure 6-digit numeric OTP (e.g. "729410")
pub fn generate_otp_code() -> String {
    let mut rng = rand::thread_rng();
    let num: u32 = rng.gen_range(100_000..=999_999);
    num.to_string()
}

/// Generate a random URL-safe hex string of `bytes_len` entropy
pub fn generate_random_token(bytes_len: usize) -> String {
    let mut bytes = vec![0u8; bytes_len];
    rand::thread_rng().fill(&mut bytes[..]);
    hex::encode(bytes)
}

/// Generate a Personal Access Token string with standard 'nex_live_' prefix
pub fn generate_pat_token() -> (String, String, String) {
    let secret = generate_random_token(32); // 64-char hex
    let full_token = format!("nex_live_{}", secret);
    let prefix = format!("nex_live_{}...", &secret[..8]);
    let hash = sha256_hash(&full_token);
    (full_token, prefix, hash)
}

/// SHA-256 hash helper for indexing tokens/OTPs safely in database
pub fn sha256_hash(data: &str) -> String {
    use sha2::{Digest, Sha256};
    let mut hasher = Sha256::new();
    hasher.update(data.as_bytes());
    hex::encode(hasher.finalize())
}

/// Sign a JWT token with claims using the provided secret key
pub fn sign_jwt(claims: &TokenClaims, secret: &[u8]) -> Result<String, String> {
    let header = Header::default();
    encode(&header, claims, &EncodingKey::from_secret(secret))
        .map_err(|e| format!("JWT signing failed: {}", e))
}

/// Validate and decode a JWT token string using the provided secret key
pub fn verify_jwt(token: &str, secret: &[u8]) -> Result<TokenClaims, String> {
    let mut validation = Validation::default();
    validation.validate_exp = true;
    decode::<TokenClaims>(token, &DecodingKey::from_secret(secret), &validation)
        .map(|data| data.claims)
        .map_err(|e| format!("Invalid or expired JWT: {}", e))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_password_hashing() {
        let pass = "SuperSecretPassword123!";
        let hash = hash_password(pass).unwrap();
        assert!(verify_password(pass, &hash));
        assert!(!verify_password("WrongPassword", &hash));
    }

    #[test]
    fn test_security_answer() {
        let answer = "   Blueberry Muffin  ";
        let hash = hash_security_answer(answer).unwrap();
        assert!(verify_security_answer("blueberry muffin", &hash));
        assert!(verify_security_answer("BLUEBERRY MUFFIN", &hash));
        assert!(!verify_security_answer("chocolate chip", &hash));
    }

    #[test]
    fn test_otp_code() {
        let code = generate_otp_code();
        assert_eq!(code.len(), 6);
        assert!(code.chars().all(|c| c.is_ascii_digit()));
    }

    #[test]
    fn test_jwt_roundtrip() {
        let secret = b"my_super_test_secret_key_1234567890";
        let now = chrono::Utc::now().timestamp() as usize;
        let claims = TokenClaims {
            sub: "usr_1".to_string(),
            email: "faizal@test.uk".to_string(),
            name: "Faizal".to_string(),
            roles: vec!["owner".to_string(), "admin".to_string()],
            token_version: 1,
            device_id: Some("dev_123".to_string()),
            exp: now + 3600,
            iat: now,
            jti: "uuid_test".to_string(),
        };

        let token = sign_jwt(&claims, secret).unwrap();
        let decoded = verify_jwt(&token, secret).unwrap();
        assert_eq!(decoded.sub, "usr_1");
        assert_eq!(decoded.email, "faizal@test.uk");
        assert_eq!(decoded.roles, vec!["owner", "admin"]);
    }
}
