# Jenkins Generic Pipeline - GitHub Copilot Instructions

## Project Overview
This project aims to create a Jenkins Generic Pipeline that can parse YAML files conforming to the GitHub Actions workflow specification and execute them within Jenkins Pipeline environments. The implementation will use Python to develop custom actions that follow GitHub Actions specifications.

## Core Objectives
- Parse GitHub Actions workflow YAML files
- Convert GitHub Actions workflow syntax to Jenkins Pipeline code
- Execute the converted workflows in Jenkins
- Develop custom Python-based actions following GitHub Actions specifications

## Key Components
1. **YAML Parser**: Responsible for reading and interpreting GitHub Actions workflow files
2. **Syntax Converter**: Transforms GitHub Actions syntax to Jenkins Pipeline DSL
3. **Execution Engine**: Runs the converted workflow in Jenkins
4. **Custom Action Framework**: Python-based implementation of action runners that conform to GitHub Actions specifications

## Technical Considerations
- Handle GitHub Actions workflow features (jobs, steps, needs, etc.)
- Support GitHub Actions events and triggers
- Map GitHub Actions environment variables to Jenkins equivalents
- Implement GitHub Actions context objects in Jenkins
- Support conditional execution and matrix strategies
- Develop Python modules for executing custom actions

## Implementation Guidelines
- Use Jenkins Shared Libraries for reusable code
- Implement proper error handling for unsupported GitHub Actions features
- Create detailed logs to aid in debugging conversion issues
- Support environment variable substitution
- Handle secrets and credentials appropriately
- Use Python for developing custom action runners and core functionality
- Maintain compatibility with GitHub Actions specification while implementing custom functionality

## Example Workflow
When advising on code implementation, consider the typical workflow:
1. User defines a GitHub Actions workflow in YAML
2. Jenkins job loads the YAML file
3. Parser converts the workflow to Jenkins Pipeline code
4. Jenkins executes the converted Pipeline

## Feature Priorities
- Essential GitHub Actions syntax support (jobs, steps, if conditions)
- Common action types (checkout, setup-node, etc.)
- Job dependencies and parallel execution
- Matrix builds support
- Environment variables and contexts

## Project Structure Conventions
- Organize code with clean separation of concerns
- Write thorough documentation and tests
- Implement modular design for easier maintenance

## Testing Approach
- Unit test YAML parsing logic
- Integration tests for full conversion process
- Sample workflows for validation
