use std::env;
use std::fs;
use std::path::PathBuf;
use std::process::Command;

fn main() {
    #[cfg(windows)]
    {
        let manifest_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
        let resources_dir = manifest_dir.join("resources");
        let rc_file = resources_dir.join("app.rc");
        let _ = fs::write(&rc_file, "1 ICON \"app.ico\"\r\n");

        let target_env = env::var("CARGO_CFG_TARGET_ENV").unwrap_or_default();
        if target_env == "gnu" {
            // MinGW windres - execute inside resources_dir to avoid space-in-path issues with gcc preprocessor
            let out_obj = resources_dir.join("resource.o");
            let status = Command::new("windres")
                .current_dir(&resources_dir)
                .args(["-O", "coff", "-i", "app.rc", "-o", "resource.o"])
                .status();

            if let Ok(s) = status {
                if s.success() && out_obj.exists() {
                    println!("cargo:rustc-link-arg=resources/resource.o");
                    println!("cargo:rerun-if-changed=resources/app.ico");
                    return;
                }
            }
        }

        // MSVC or fallback to winres
        let mut res = winres::WindowsResource::new();
        res.set_icon("resources/app.ico");
        let _ = res.compile();
        println!("cargo:rerun-if-changed=resources/app.ico");
    }
}
