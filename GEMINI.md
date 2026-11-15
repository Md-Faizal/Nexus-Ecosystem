# Workspace Build & Compilation Policy

- **NEVER compile on the cluster VMs (`my-mac`, `cos`, `pro`)**: Under no circumstances run `cargo build`, `cargo check`, `npm run build`, `gcc`, or compiler tasks on the remote VMs.
- **Always build in GitHub Actions CI**: Release binaries and artifact generation belong in CI.
- If pre-verification is needed, test/compile locally, never on the production nodes.
