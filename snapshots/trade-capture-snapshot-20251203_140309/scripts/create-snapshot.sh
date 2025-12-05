#!/bin/bash

################################################################################
# Trade Capture System - Codebase Snapshot Script
# 
# This script creates a complete snapshot of the trade-capture codebase
# including source code, configuration, documentation, and system state.
#
# Usage: ./create-snapshot.sh [output-dir]
#
# Output: A timestamped directory containing the full codebase snapshot
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_DIR="${1:-$PROJECT_ROOT/snapshots}"
SNAPSHOT_NAME="trade-capture-snapshot-$TIMESTAMP"
SNAPSHOT_PATH="$OUTPUT_DIR/$SNAPSHOT_NAME"

################################################################################
# Helper Functions
################################################################################

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

create_directory() {
    local dir="$1"
    if [ ! -d "$dir" ]; then
        mkdir -p "$dir"
        log_info "Created directory: $dir"
    fi
}

################################################################################
# Main Snapshot Process
################################################################################

main() {
    echo ""
    echo "========================================================================"
    echo "  Trade Capture System - Codebase Snapshot"
    echo "========================================================================"
    echo ""
    
    log_info "Starting snapshot creation at $(date)"
    log_info "Project root: $PROJECT_ROOT"
    log_info "Output directory: $OUTPUT_DIR"
    log_info "Snapshot name: $SNAPSHOT_NAME"
    echo ""
    
    # Create output directory
    create_directory "$OUTPUT_DIR"
    create_directory "$SNAPSHOT_PATH"
    
    # 1. Copy source code
    log_info "Step 1/10: Copying source code..."
    copy_source_code
    
    # 2. Copy configuration files
    log_info "Step 2/10: Copying configuration files..."
    copy_configurations
    
    # 3. Copy documentation
    log_info "Step 3/10: Copying documentation..."
    copy_documentation
    
    # 4. Copy build files
    log_info "Step 4/10: Copying build files..."
    copy_build_files
    
    # 5. Copy scripts
    log_info "Step 5/10: Copying scripts..."
    copy_scripts
    
    # 6. Capture system state
    log_info "Step 6/10: Capturing system state..."
    capture_system_state
    
    # 7. Capture database schema
    log_info "Step 7/10: Capturing database schema..."
    capture_database_schema
    
    # 8. Capture Docker state
    log_info "Step 8/10: Capturing Docker state..."
    capture_docker_state
    
    # 9. Create metadata
    log_info "Step 9/10: Creating snapshot metadata..."
    create_metadata
    
    # 10. Create archive
    log_info "Step 10/10: Creating compressed archive..."
    create_archive
    
    # Summary
    echo ""
    echo "========================================================================"
    log_success "Snapshot creation complete!"
    echo "========================================================================"
    echo ""
    log_info "Snapshot location: $SNAPSHOT_PATH"
    log_info "Archive location: ${SNAPSHOT_PATH}.tar.gz"
    echo ""
    log_info "Snapshot contents:"
    du -sh "$SNAPSHOT_PATH"
    echo ""
}

################################################################################
# Step Functions
################################################################################

copy_source_code() {
    local src_dir="$SNAPSHOT_PATH/source"
    create_directory "$src_dir"
    
    # Main application source
    if [ -d "$PROJECT_ROOT/src" ]; then
        cp -r "$PROJECT_ROOT/src" "$src_dir/main-app"
        log_success "Copied main application source"
    fi
    
    # Simulator source
    if [ -d "$PROJECT_ROOT/simulator/src" ]; then
        cp -r "$PROJECT_ROOT/simulator/src" "$src_dir/simulator"
        log_success "Copied simulator source"
    fi
    
    # Kafka consumer source
    if [ -d "$PROJECT_ROOT/kafka-consumer/src" ]; then
        cp -r "$PROJECT_ROOT/kafka-consumer/src" "$src_dir/kafka-consumer"
        log_success "Copied kafka-consumer source"
    fi
}

copy_configurations() {
    local config_dir="$SNAPSHOT_PATH/config"
    create_directory "$config_dir"
    
    # Docker compose
    if [ -f "$PROJECT_ROOT/docker-compose.yaml" ]; then
        cp "$PROJECT_ROOT/docker-compose.yaml" "$config_dir/"
        log_success "Copied docker-compose.yaml"
    fi
    
    # Dockerfiles
    if [ -f "$PROJECT_ROOT/Dockerfile" ]; then
        cp "$PROJECT_ROOT/Dockerfile" "$config_dir/Dockerfile.main"
    fi
    if [ -f "$PROJECT_ROOT/simulator/Dockerfile" ]; then
        cp "$PROJECT_ROOT/simulator/Dockerfile" "$config_dir/Dockerfile.simulator"
    fi
    
    # Application properties
    find "$PROJECT_ROOT" -name "application.yaml" -o -name "application.properties" | while read -r file; do
        local rel_path=$(realpath --relative-to="$PROJECT_ROOT" "$file")
        local dest_dir="$config_dir/$(dirname "$rel_path")"
        create_directory "$dest_dir"
        cp "$file" "$dest_dir/"
    done
    
    log_success "Copied configuration files"
}

copy_documentation() {
    local docs_dir="$SNAPSHOT_PATH/docs"
    create_directory "$docs_dir"
    
    # Copy markdown files
    find "$PROJECT_ROOT" -maxdepth 1 -name "*.md" -exec cp {} "$docs_dir/" \;
    
    # Copy specific documentation
    [ -f "$PROJECT_ROOT/TESTING-GUIDE.md" ] && cp "$PROJECT_ROOT/TESTING-GUIDE.md" "$docs_dir/"
    [ -f "$PROJECT_ROOT/TEST-INSTRUCTIONS.md" ] && cp "$PROJECT_ROOT/TEST-INSTRUCTIONS.md" "$docs_dir/"
    [ -f "$PROJECT_ROOT/refactor.prompt.md" ] && cp "$PROJECT_ROOT/refactor.prompt.md" "$docs_dir/"
    
    log_success "Copied documentation"
}

copy_build_files() {
    local build_dir="$SNAPSHOT_PATH/build"
    create_directory "$build_dir"
    
    # Maven files
    find "$PROJECT_ROOT" -name "pom.xml" | while read -r file; do
        local rel_path=$(realpath --relative-to="$PROJECT_ROOT" "$file")
        local dest_dir="$build_dir/$(dirname "$rel_path")"
        create_directory "$dest_dir"
        cp "$file" "$dest_dir/"
    done
    
    # Maven wrapper
    if [ -f "$PROJECT_ROOT/mvnw" ]; then
        cp "$PROJECT_ROOT/mvnw" "$build_dir/"
        cp "$PROJECT_ROOT/mvnw.cmd" "$build_dir/"
        cp -r "$PROJECT_ROOT/.mvn" "$build_dir/" 2>/dev/null || true
    fi
    
    log_success "Copied build files"
}

copy_scripts() {
    local scripts_dir="$SNAPSHOT_PATH/scripts"
    create_directory "$scripts_dir"
    
    # Copy script directories
    if [ -d "$PROJECT_ROOT/scripts" ]; then
        cp -r "$PROJECT_ROOT/scripts/"* "$scripts_dir/" 2>/dev/null || true
    fi
    
    # Copy shell scripts from root
    find "$PROJECT_ROOT" -maxdepth 1 -name "*.sh" -exec cp {} "$scripts_dir/" \;
    
    log_success "Copied scripts"
}

capture_system_state() {
    local state_dir="$SNAPSHOT_PATH/state"
    create_directory "$state_dir"
    
    # Git information
    if [ -d "$PROJECT_ROOT/.git" ]; then
        log_info "Capturing git state..."
        {
            echo "=== Git Status ==="
            git -C "$PROJECT_ROOT" status
            echo ""
            echo "=== Git Log (last 10 commits) ==="
            git -C "$PROJECT_ROOT" log --oneline -10
            echo ""
            echo "=== Git Branch ==="
            git -C "$PROJECT_ROOT" branch -v
            echo ""
            echo "=== Git Remote ==="
            git -C "$PROJECT_ROOT" remote -v
            echo ""
            echo "=== Git Diff (uncommitted changes) ==="
            git -C "$PROJECT_ROOT" diff
        } > "$state_dir/git-state.txt" 2>&1
        log_success "Captured git state"
    fi
    
    # Dependency tree
    if command -v mvn &> /dev/null; then
        log_info "Capturing Maven dependency tree..."
        (cd "$PROJECT_ROOT" && mvn dependency:tree -DoutputFile="$state_dir/dependency-tree.txt" -q 2>&1) || log_warn "Failed to capture dependency tree"
    fi
    
    # Java version
    if command -v java &> /dev/null; then
        java -version > "$state_dir/java-version.txt" 2>&1
        log_success "Captured Java version"
    fi
    
    # Maven version
    if command -v mvn &> /dev/null; then
        mvn -version > "$state_dir/maven-version.txt" 2>&1
        log_success "Captured Maven version"
    fi
}

capture_database_schema() {
    local db_dir="$SNAPSHOT_PATH/database"
    create_directory "$db_dir"
    
    # Check if postgres container is running
    if docker ps | grep -q postgres; then
        log_info "Capturing database schema..."
        
        # Dump schema
        docker exec postgres pg_dump -U pms -d pmsdb --schema-only > "$db_dir/schema.sql" 2>/dev/null || log_warn "Failed to dump database schema"
        
        # Table counts
        {
            echo "=== Table Counts ==="
            docker exec -i postgres psql -U pms -d pmsdb -c "
                SELECT 
                    schemaname, tablename, 
                    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size,
                    n_live_tup as row_count
                FROM pg_stat_user_tables 
                ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
            " 2>/dev/null
        } > "$db_dir/table-stats.txt"
        
        # Liquibase changelogs
        if [ -d "$PROJECT_ROOT/src/main/resources/db/changelog" ]; then
            cp -r "$PROJECT_ROOT/src/main/resources/db/changelog" "$db_dir/"
            log_success "Copied Liquibase changelogs"
        fi
        
        log_success "Captured database schema"
    else
        log_warn "PostgreSQL container not running, skipping database capture"
    fi
}

capture_docker_state() {
    local docker_dir="$SNAPSHOT_PATH/docker"
    create_directory "$docker_dir"
    
    if command -v docker &> /dev/null; then
        log_info "Capturing Docker state..."
        
        # Container status
        docker ps -a --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}" > "$docker_dir/containers.txt" 2>/dev/null || log_warn "Failed to capture container status"
        
        # Images
        docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" > "$docker_dir/images.txt" 2>/dev/null || log_warn "Failed to capture images"
        
        # Networks
        docker network ls > "$docker_dir/networks.txt" 2>/dev/null || true
        
        # Volumes
        docker volume ls > "$docker_dir/volumes.txt" 2>/dev/null || true
        
        # Docker Compose state
        if [ -f "$PROJECT_ROOT/docker-compose.yaml" ]; then
            (cd "$PROJECT_ROOT" && docker-compose config > "$docker_dir/docker-compose-resolved.yaml" 2>/dev/null) || true
        fi
        
        # Logs from running containers
        for container in $(docker ps --format "{{.Names}}" 2>/dev/null); do
            docker logs "$container" --tail 100 > "$docker_dir/logs-$container.txt" 2>&1 || true
        done
        
        log_success "Captured Docker state"
    else
        log_warn "Docker not available, skipping Docker state capture"
    fi
}

create_metadata() {
    local meta_file="$SNAPSHOT_PATH/SNAPSHOT-INFO.txt"
    
    {
        echo "========================================================================"
        echo "  Trade Capture System - Snapshot Metadata"
        echo "========================================================================"
        echo ""
        echo "Snapshot Timestamp: $(date)"
        echo "Snapshot Name: $SNAPSHOT_NAME"
        echo "Created By: $(whoami)@$(hostname)"
        echo "Working Directory: $(pwd)"
        echo ""
        echo "========================================================================"
        echo "  System Information"
        echo "========================================================================"
        echo ""
        echo "OS: $(uname -s)"
        echo "Kernel: $(uname -r)"
        echo "Architecture: $(uname -m)"
        echo ""
        
        if command -v java &> /dev/null; then
            echo "Java Version:"
            java -version 2>&1 | head -3
            echo ""
        fi
        
        if command -v mvn &> /dev/null; then
            echo "Maven Version:"
            mvn -version | head -1
            echo ""
        fi
        
        if command -v docker &> /dev/null; then
            echo "Docker Version:"
            docker --version
            echo ""
            echo "Docker Compose Version:"
            docker-compose --version 2>/dev/null || echo "Not available"
            echo ""
        fi
        
        echo "========================================================================"
        echo "  Git Information"
        echo "========================================================================"
        echo ""
        if [ -d "$PROJECT_ROOT/.git" ]; then
            echo "Current Branch: $(git -C "$PROJECT_ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'Unknown')"
            echo "Latest Commit: $(git -C "$PROJECT_ROOT" log -1 --oneline 2>/dev/null || echo 'Unknown')"
            echo "Uncommitted Changes: $(git -C "$PROJECT_ROOT" status --short | wc -l) files"
        else
            echo "Not a git repository"
        fi
        echo ""
        
        echo "========================================================================"
        echo "  Snapshot Contents"
        echo "========================================================================"
        echo ""
        echo "Directory Structure:"
        tree -L 2 "$SNAPSHOT_PATH" 2>/dev/null || find "$SNAPSHOT_PATH" -type d -maxdepth 2 | sort
        echo ""
        
        echo "========================================================================"
        echo "  File Counts"
        echo "========================================================================"
        echo ""
        echo "Java Files: $(find "$SNAPSHOT_PATH" -name "*.java" 2>/dev/null | wc -l)"
        echo "YAML Files: $(find "$SNAPSHOT_PATH" -name "*.yaml" -o -name "*.yml" 2>/dev/null | wc -l)"
        echo "XML Files: $(find "$SNAPSHOT_PATH" -name "*.xml" 2>/dev/null | wc -l)"
        echo "Proto Files: $(find "$SNAPSHOT_PATH" -name "*.proto" 2>/dev/null | wc -l)"
        echo "Markdown Files: $(find "$SNAPSHOT_PATH" -name "*.md" 2>/dev/null | wc -l)"
        echo "Shell Scripts: $(find "$SNAPSHOT_PATH" -name "*.sh" 2>/dev/null | wc -l)"
        echo ""
        
        echo "========================================================================"
        echo "  Component Status (at time of snapshot)"
        echo "========================================================================"
        echo ""
        if docker ps &> /dev/null; then
            echo "Running Docker Containers:"
            docker ps --format "  - {{.Names}}: {{.Status}}" 2>/dev/null || echo "  None"
        else
            echo "Docker not available"
        fi
        echo ""
        
    } > "$meta_file"
    
    log_success "Created snapshot metadata"
}

create_archive() {
    local archive_name="${SNAPSHOT_NAME}.tar.gz"
    local archive_path="$OUTPUT_DIR/$archive_name"
    
    log_info "Creating compressed archive..."
    (cd "$OUTPUT_DIR" && tar -czf "$archive_name" "$SNAPSHOT_NAME" 2>/dev/null)
    
    if [ -f "$archive_path" ]; then
        local size=$(du -h "$archive_path" | cut -f1)
        log_success "Created archive: $archive_name (Size: $size)"
        
        # Generate checksum
        if command -v sha256sum &> /dev/null; then
            sha256sum "$archive_path" > "${archive_path}.sha256"
            log_success "Generated SHA256 checksum"
        fi
    else
        log_error "Failed to create archive"
        exit 1
    fi
}

################################################################################
# Script Entry Point
################################################################################

# Ensure we're in the project root
cd "$PROJECT_ROOT"

# Run main function
main

# Display final summary
echo ""
echo "========================================================================"
echo "  Snapshot Summary"
echo "========================================================================"
echo ""
echo "📁 Snapshot Directory: $SNAPSHOT_PATH"
echo "📦 Archive File: ${SNAPSHOT_PATH}.tar.gz"
echo "✅ Checksum File: ${SNAPSHOT_PATH}.tar.gz.sha256"
echo ""
echo "To restore from this snapshot:"
echo "  1. Extract: tar -xzf ${SNAPSHOT_NAME}.tar.gz"
echo "  2. Review: cat ${SNAPSHOT_NAME}/SNAPSHOT-INFO.txt"
echo "  3. Follow instructions in ${SNAPSHOT_NAME}/docs/TESTING-GUIDE.md"
echo ""
echo "========================================================================"

exit 0
