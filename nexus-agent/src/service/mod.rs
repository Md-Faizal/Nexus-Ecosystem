#[cfg(target_os = "linux")]
pub mod systemd;

#[cfg(target_os = "windows")]
pub mod windows;

#[cfg(target_os = "macos")]
pub mod launchd;

pub struct ServiceManager;

impl ServiceManager {
    pub fn install(user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        #[cfg(target_os = "windows")]
        {
            let _ = user_mode;
            windows::WindowsInstaller::install()
        }

        #[cfg(target_os = "linux")]
        {
            systemd::SystemdInstaller::install(user_mode)
        }

        #[cfg(target_os = "macos")]
        {
            launchd::LaunchdInstaller::install(user_mode)
        }

        #[cfg(not(any(target_os = "windows", target_os = "linux", target_os = "macos")))]
        {
            let _ = user_mode;
            Err("Unsupported OS for native service manager".into())
        }
    }

    pub fn uninstall(user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        #[cfg(target_os = "windows")]
        {
            let _ = user_mode;
            windows::WindowsInstaller::uninstall()
        }

        #[cfg(target_os = "linux")]
        {
            systemd::SystemdInstaller::uninstall(user_mode)
        }

        #[cfg(target_os = "macos")]
        {
            launchd::LaunchdInstaller::uninstall(user_mode)
        }

        #[cfg(not(any(target_os = "windows", target_os = "linux", target_os = "macos")))]
        {
            let _ = user_mode;
            Err("Unsupported OS for native service manager".into())
        }
    }
}

