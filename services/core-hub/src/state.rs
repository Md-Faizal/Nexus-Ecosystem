use crate::config::AppConfig;
use crate::db::DbPool;
use crate::routes::mesh_api::FleetHubState;
use std::sync::Arc;

pub struct AppStateInner {
    pub config: AppConfig,
    pub db: DbPool,
    pub fleet: Arc<FleetHubState>,
}

pub type SharedState = Arc<AppStateInner>;
