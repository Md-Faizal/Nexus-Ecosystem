pub fn print_banner() {
    // Stylized ASCII art inspired by Gemini and Claude Code
    println!("\x1b[38;2;255;110;180m██████╗ \x1b[38;2;230;120;210m███╗   ██╗\x1b[38;2;190;140;240m███████╗\x1b[38;2;150;160;255m██╗   ██╗\x1b[38;2;100;190;255m███████╗    \x1b[38;2;70;220;255m███████╗\x1b[38;2;80;235;235m██╗     \x1b[38;2;100;245;210m███████╗\x1b[38;2;120;250;180m███████╗\x1b[38;2;140;255;150m████████╗\x1b[0m");
    println!("\x1b[38;2;255;110;180m██╔══██╗\x1b[38;2;230;120;210m████╗  ██║\x1b[38;2;190;140;240m██╔════╝\x1b[38;2;150;160;255m██║   ██║\x1b[38;2;100;190;255m██╔════╝    \x1b[38;2;70;220;255m██╔════╝\x1b[38;2;80;235;235m██║     \x1b[38;2;100;245;210m██╔════╝\x1b[38;2;120;250;180m██╔════╝\x1b[38;2;140;255;150m╚══██╔══╝\x1b[0m");
    println!("\x1b[38;2;255;110;180m██║  ██║\x1b[38;2;230;120;210m██╔██╗ ██║\x1b[38;2;190;140;240m█████╗  \x1b[38;2;150;160;255m██║   ██║\x1b[38;2;100;190;255m███████╗    \x1b[38;2;70;220;255m█████╗  \x1b[38;2;80;235;235m██║     \x1b[38;2;100;245;210m█████╗  \x1b[38;2;120;250;180m█████╗     \x1b[38;2;140;255;150m██║   \x1b[0m");
    println!("\x1b[38;2;255;110;180m██║  ██║\x1b[38;2;230;120;210m██║╚██╗██║\x1b[38;2;190;140;240m██╔══╝  \x1b[38;2;150;160;255m██║   ██║\x1b[38;2;100;190;255m╚════██║    \x1b[38;2;70;220;255m██╔══╝  \x1b[38;2;80;235;235m██║     \x1b[38;2;100;245;210m██╔══╝  \x1b[38;2;120;250;180m██╔══╝     \x1b[38;2;140;255;150m██║   \x1b[0m");
    println!("\x1b[38;2;255;110;180m██████╔╝\x1b[38;2;230;120;210m██║ ╚████║\x1b[38;2;190;140;240m███████╗\x1b[38;2;150;160;255m╚██████╔╝\x1b[38;2;100;190;255m███████║    \x1b[38;2;70;220;255m██║     \x1b[38;2;80;235;235m███████╗\x1b[38;2;100;245;210m███████╗\x1b[38;2;120;250;180m███████╗   \x1b[38;2;140;255;150m██║   \x1b[0m");
    println!("\x1b[38;2;255;110;180m╚═════╝ \x1b[38;2;230;120;210m╚═╝  ╚═══╝\x1b[38;2;190;140;240m╚══════╝ \x1b[38;2;150;160;255m╚═════╝ \x1b[38;2;100;190;255m╚══════╝    \x1b[38;2;70;220;255m╚═╝     \x1b[38;2;80;235;235m╚══════╝\x1b[38;2;100;245;210m╚══════╝\x1b[38;2;120;250;180m╚══════╝   \x1b[38;2;140;255;150m╚═╝   \x1b[0m");
    println!();
}

pub fn print_welcome_box() {
    println!("\x1b[38;2;255;140;90m╭─────────────────────────────────────────────────────────────────────────────╮\x1b[0m");
    println!("\x1b[38;2;255;140;90m│\x1b[0m \x1b[1;37m* Welcome to Nexus Fleet AI Terminal (Research & Remote Node Preview)        \x1b[0m\x1b[38;2;255;140;90m│\x1b[0m");
    println!("\x1b[38;2;255;140;90m╰─────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
    println!();
    println!("\x1b[1;37mTips for getting started:\x1b[0m");
    println!("  1. Ask questions, stream local inference, or inspect fleet compute nodes.");
    println!("  2. Pre-load / unload AI model weights to directly control RAM and VRAM.");
    println!("  3. Type \x1b[38;2;255;110;180m/help\x1b[0m or \x1b[38;2;255;110;180m/?\x1b[0m for interactive manual & full slash command catalog.");
    println!("  4. Type \x1b[38;2;255;110;180m/bye\x1b[0m or \x1b[38;2;255;110;180m/exit\x1b[0m to quit the session.");
    println!();
}

pub fn print_status_box(node_id: &str, hostname: &str, ai_endpoint: &str, is_online: bool) {
    let status_str = if is_online {
        "\x1b[1;32m● ONLINE (faizal.uk)\x1b[0m"
    } else {
        "\x1b[1;31m○ OFFLINE\x1b[0m"
    };

    println!("\x1b[38;2;250;200;80m╭─────────────────────────────────────────────────────────────────────────────╮\x1b[0m");
    println!("\x1b[38;2;250;200;80m│\x1b[0m Nexus Fleet Agent v{} • Hub: {} ", env!("CARGO_PKG_VERSION"), status_str);
    println!("\x1b[38;2;250;200;80m│\x1b[0m Node: \x1b[1;36m{}\x1b[0m ({}) • Ollama Engine: \x1b[1;33m{}\x1b[0m", node_id, hostname, ai_endpoint);
    println!("\x1b[38;2;250;200;80m╰─────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
    println!();
}

pub fn print_prompt_box(active_model: &str, vram_info: &str) {
    println!("\x1b[90mActive Model: \x1b[1;36m{}\x1b[0m \x1b[90m| {}\x1b[0m", active_model, vram_info);
    println!("\x1b[38;2;80;200;220m╭─────────────────────────────────────────────────────────────────────────────╮\x1b[0m");
    println!("\x1b[38;2;80;200;220m│\x1b[0m  \x1b[1;35m>\x1b[0m \x1b[90mType your prompt or /help, /models, /ps, /load, /unload, /bye...\x1b[0m       \x1b[38;2;80;200;220m│\x1b[0m");
    println!("\x1b[38;2;80;200;220m╰─────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
}

