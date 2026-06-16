#!/bin/bash

echo "Setting up CloudLedger development environment..."

check_success() {
    if [ $? -eq 0 ]; then
        echo "  $1 OK"
    else
        echo "  $1 FAILED"
        exit 1
    fi
}

export CI=true
WORKSPACE_ROOT=$(pwd)
echo "Workspace: $WORKSPACE_ROOT"

# Java
java -version
check_success "Java 21"

if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME="/usr/local/sdkman/candidates/java/current"
    echo "export JAVA_HOME=/usr/local/sdkman/candidates/java/current" >> ~/.bashrc
fi

# api (Spring Boot)
if [ -f "$WORKSPACE_ROOT/api/gradlew" ]; then
    chmod +x "$WORKSPACE_ROOT/api/gradlew"
    "$WORKSPACE_ROOT/api/gradlew" -p "$WORKSPACE_ROOT/api" build -x test
    check_success "api gradle build"
else
    echo "  api/gradlew not found, skipping"
fi

# projector (Lambda — TypeScript)
if [ -f "$WORKSPACE_ROOT/projector/package.json" ]; then
    npm ci --prefix "$WORKSPACE_ROOT/projector"
    check_success "projector npm install"
else
    echo "  projector/package.json not found, skipping"
fi

# AWS CLI — point at floci by default for local dev
aws configure set aws_access_key_id test
aws configure set aws_secret_access_key test
aws configure set region us-east-1
aws configure set output json
echo "export AWS_ENDPOINT_URL=http://floci:4566" >> ~/.bashrc

echo "Setup complete."
echo ""
echo "Commands:"
echo "  Start local stack : docker compose up -d"
echo "  Terraform (local) : cd terraform && terraform init -backend-config=backends/local.hcl && terraform apply -var-file=envs/local.tfvars"
echo "  API               : cd api && ./gradlew bootRun"
echo "  Projector         : cd projector && npm run dev"
