use std::io::{self, Write};
use std::process::Command;

pub struct ChatRenderer;

/// Renders streaming tokens with markdown code-block left-border styling
pub struct StreamFormatter {
    in_code_block: bool,
    code_lang: String,
    line_buffer: String,
}

impl StreamFormatter {
    pub fn new() -> Self {
        Self {
            in_code_block: false,
            code_lang: String::new(),
            line_buffer: String::new(),
        }
    }

    pub fn feed_chunk(&mut self, text: &str) {
        for ch in text.chars() {
            if ch == '\n' {
                self.flush_line();
                print!("\n");
                let _ = io::stdout().flush();
            } else {
                self.line_buffer.push(ch);
            }
        }
    }

    pub fn finish(&mut self) {
        if !self.line_buffer.is_empty() {
            self.flush_line();
            print!("\n");
            let _ = io::stdout().flush();
        }
    }

    fn flush_line(&mut self) {
        let line = std::mem::take(&mut self.line_buffer);

        if line.trim_start().starts_with("```") {
            if !self.in_code_block {
                self.in_code_block = true;
                self.code_lang = line.trim_start().trim_start_matches('`').trim().to_string();
                let lang_tag = if self.code_lang.is_empty() { "code" } else { &self.code_lang };
                print!("\x1b[38;2;100;180;255m╭── {} ──────────────────────────────────────────────────────────\x1b[0m", lang_tag);
            } else {
                self.in_code_block = false;
                self.code_lang.clear();
                print!("\x1b[38;2;100;180;255m╰───────────────────────────────────────────────────────────────────────\x1b[0m");
            }
            return;
        }

        if self.in_code_block {
            print!("\x1b[38;2;100;180;255m│ \x1b[38;2;220;235;255m{}\x1b[0m", line);
        } else {
            print!("\x1b[38;2;240;245;255m{}\x1b[0m", line);
        }
    }
}

impl ChatRenderer {
    /// Renders a Claude / Gemini CLI class top status pill
    pub fn print_session_header(
        node_name: &str,
        model_name: &str,
        vram_info: Option<&str>,
        context_tokens: usize,
        title: &str,
    ) {
        let vram_badge = match vram_info {
            Some(v) if !v.is_empty() => format!(" • \x1b[38;2;255;200;80m{}\x1b[0m", v),
            _ => String::new(),
        };

        println!("\x1b[38;2;90;130;255m╭─────────────────────────────────────────────────────────────────────────────╮\x1b[0m");
        println!(
            "\x1b[38;2;90;130;255m│\x1b[0m \x1b[1;37m🤖 Nexus AI Terminal\x1b[0m • \x1b[1;32m{}\x1b[0m • Model: \x1b[1;33m{}\x1b[0m{}{}",
            node_name, model_name, vram_badge,
            if context_tokens > 0 {
                format!(" • \x1b[90m[~{} tok]\x1b[0m", context_tokens)
            } else {
                String::new()
            }
        );
        println!(
            "\x1b[38;2;90;130;255m│\x1b[0m \x1b[90mSession: \"{}\" • Type \x1b[1;36m/help\x1b[0m\x1b[90m for slash commands\x1b[0m",
            title
        );
        println!("\x1b[38;2;90;130;255m╰─────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
        println!();
    }

    /// Latency, throughput, and token count footer
    pub fn print_metrics_footer(
        tok_sec: f64,
        tokens: u64,
        elapsed_sec: f64,
        target_name: &str,
        model_name: &str,
    ) {
        println!(
            "\x1b[90m⚡ {:.1} tok/s • {} tokens • {:.2}s via {} ({})\x1b[0m\n",
            tok_sec, tokens, elapsed_sec, target_name, model_name
        );
    }

    /// Renders categorized operator slash commands manual
    pub fn print_slash_help() {
        println!("\x1b[1;36m╭─ ⚡ NEXUS OPERATOR SLASH COMMANDS ──────────────────────────────────────────╮\x1b[0m");
        println!("│  \x1b[1;33mMODEL & COMPUTE CONTROLS:\x1b[0m");
        println!("│    \x1b[1;32m/model [name|#]\x1b[0m        Switch model on active PC (or list options)");
        println!("│    \x1b[1;32m/node [name|#]\x1b[0m         Switch compute target PC (potato-pro, local, etc.)");
        println!("│    \x1b[1;32m/stop\x1b[0m / \x1b[1;32m/unload\x1b[0m        Evict/unload active model from GPU VRAM immediately");
        println!("│    \x1b[1;32m/ps\x1b[0m                    Show all models actively occupying GPU VRAM");
        println!("│    \x1b[1;32m/pull <model>\x1b[0m          Pull/download new Ollama model to target PC");
        println!("│");
        println!("│  \x1b[1;33mCONTEXT & CONVERSATION MANAGEMENT:\x1b[0m");
        println!("│    \x1b[1;32m/clear\x1b[0m / \x1b[1;32m/reset\x1b[0m        Wipe context window memory (keep model warm)");
        println!("│    \x1b[1;32m/save [title]\x1b[0m          Save session transcript to disk & cloud");
        println!("│    \x1b[1;32m/load [id|#]\x1b[0m           Resume past conversation transcript");
        println!("│    \x1b[1;32m/history\x1b[0m               List all saved conversations with turn counts");
        println!("│    \x1b[1;32m/delete [id|#]\x1b[0m         Delete conversation record from disk and cloud");
        println!("│    \x1b[1;32m/export [file.md]\x1b[0m      Export session to formatted Markdown file");
        println!("│    \x1b[1;32m/copy\x1b[0m                  Copy last assistant response to system clipboard");
        println!("│");
        println!("│  \x1b[1;33mENGINE TUNING & CLUSTER ACCESS:\x1b[0m");
        println!("│    \x1b[1;32m/cluster\x1b[0m / \x1b[1;32m/nodes\x1b[0m       Inspect live 3-VM cluster nodes and hardware");
        println!("│    \x1b[1;32m/services\x1b[0m / \x1b[1;32m/apps\x1b[0m     List running cluster services and game servers");
        println!("│    \x1b[1;32m/mc\x1b[0m / \x1b[1;32m/mc status\x1b[0m         Inspect local Minecraft server PID, RSS RAM & health");
        println!("│    \x1b[1;32m/system [prompt]\x1b[0m       Inspect or set custom system instructions");
        println!("│    \x1b[1;32m/temp <0.0 - 1.0>\x1b[0m      Adjust model creativity / temperature");
        println!("│    \x1b[1;32m/pin <pin>\x1b[0m             Authenticate operator PIN (Default: 2004)");
        println!("│    \x1b[1;32m/mode chat\x1b[0m             Switch to Fleet Broadcast & Device Messaging");
        println!("│    \x1b[1;32m/exit\x1b[0m / \x1b[1;32m/bye\x1b[0m           Save & exit terminal REPL session");
        println!("\x1b[1;36m╰─────────────────────────────────────────────────────────────────────────────╯\x1b[0m\n");
    }

    /// Copies text to OS clipboard
    pub fn copy_to_clipboard(text: &str) -> Result<(), String> {
        #[cfg(target_os = "windows")]
        {
            let mut child = Command::new("powershell")
                .args(["-NoProfile", "-Command", "Set-Clipboard -Value $input"])
                .stdin(std::process::Stdio::piped())
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .spawn()
                .map_err(|e| e.to_string())?;

            if let Some(mut stdin) = child.stdin.take() {
                stdin.write_all(text.as_bytes()).map_err(|e| e.to_string())?;
            }
            let _ = child.wait();
            return Ok(());
        }

        #[cfg(target_os = "macos")]
        {
            let mut child = Command::new("pbcopy")
                .stdin(std::process::Stdio::piped())
                .spawn()
                .map_err(|e| e.to_string())?;

            if let Some(mut stdin) = child.stdin.take() {
                stdin.write_all(text.as_bytes()).map_err(|e| e.to_string())?;
            }
            let _ = child.wait();
            return Ok(());
        }

        #[cfg(target_os = "linux")]
        {
            if let Ok(mut child) = Command::new("xclip")
                .args(["-selection", "clipboard"])
                .stdin(std::process::Stdio::piped())
                .spawn()
            {
                if let Some(mut stdin) = child.stdin.take() {
                    let _ = stdin.write_all(text.as_bytes());
                }
                let _ = child.wait();
                return Ok(());
            }

            if let Ok(mut child) = Command::new("wl-copy")
                .stdin(std::process::Stdio::piped())
                .spawn()
            {
                if let Some(mut stdin) = child.stdin.take() {
                    let _ = stdin.write_all(text.as_bytes());
                }
                let _ = child.wait();
                return Ok(());
            }

            return Err("Neither xclip nor wl-copy found on system".to_string());
        }

        #[allow(unreachable_code)]
        Ok(())
    }
}
