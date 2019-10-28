# Code generation demo Plugin
**this is a proof of concept**

This is a basic maven plugin project to demonstrate the source code generation capabilities of a maven plugin. The theoretic "API" definition sits in a yaml file and provided
as a source jar dependency to the plugin. The plugin downloads and extracts the plugin and processes all yml/yaml files found inside the output folder, generates the corresponding
java source code then attaches the result to the build process as source code folder.

## Structure
The project has 3 maven modules:
* codegen-maven-plugin - the plugin sourcec
* api - a very simple project with yaml definitions and a build config to create source code attached jar 
* demo - the plugin demonstration project to download the api project's source code and generate Java from the yaml files.

## Usage
Checkout and use ```mvn install```. You can check the result in the ```<project>demo/target/generated-sources``` folder

## TODO
[x] generate a sample java source and add to the build process
[x] separate the plugin and the demo code to different modules
[x] create a simple yaml-> java PoC
[x] add a yaml parser and a small yaml example
[x] add a source code artifact configuration+download+unzip function
[x] add CI build
