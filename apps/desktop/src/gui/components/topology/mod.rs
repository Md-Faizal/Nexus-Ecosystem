pub mod canvas;
pub mod links;
pub mod node_card;
pub mod packet_engine;

pub use canvas::TopologyCanvas;
#[allow(unused_imports)]
pub use node_card::{render_node_micro_hud, RenderNodeParams, TopologyRole};
#[allow(unused_imports)]
pub use packet_engine::{PacketKind, PacketParticle};

#[cfg(test)]
mod tests {
    use super::*;
    use canvas::classify_node_role;
    use crate::gui::state::TopologyViewMode;

    #[test]
    fn test_node_role_classification() {
        assert_eq!(classify_node_role("my-mac", &[]), TopologyRole::Hub);
        assert_eq!(classify_node_role("container-of-souls", &[]), TopologyRole::Failover);
        assert_eq!(classify_node_role("cos", &[]), TopologyRole::Failover);
        assert_eq!(classify_node_role("prometheus", &[]), TopologyRole::Quorum);
        assert_eq!(classify_node_role("pro", &[]), TopologyRole::Quorum);
        assert_eq!(classify_node_role("potato-pro", &[]), TopologyRole::AiCompute);
        assert_eq!(
            classify_node_role("linux-rig", &["qwen2.5-coder:7b".to_string()]),
            TopologyRole::AiCompute
        );
        assert_eq!(classify_node_role("dev-laptop", &[]), TopologyRole::Workstation);
        assert_eq!(classify_node_role("carplay-adapter", &[]), TopologyRole::Ambient);
        assert_eq!(classify_node_role("living-room-tv", &[]), TopologyRole::Ambient);
    }

    #[test]
    fn test_topology_view_modes() {
        assert_eq!(TopologyViewMode::ALL.len(), 3);
        assert_eq!(TopologyViewMode::CanBusHybrid.label(), "CAN-Bus Hybrid");
        assert_eq!(TopologyViewMode::TieredHierarchy.label(), "Tiered Grid");
        assert_eq!(TopologyViewMode::FullMesh.label(), "Full Mesh");
    }

    #[test]
    fn test_packet_kinds_and_colors() {
        assert_eq!(PacketKind::CanTelemetry.size(), 3.0);
        assert_eq!(PacketKind::QuorumHeartbeat.size(), 2.5);
        assert_eq!(PacketKind::AiInferenceStream.size(), 3.8);
        assert_eq!(PacketKind::ControlDispatch.size(), 4.2);
    }
}
