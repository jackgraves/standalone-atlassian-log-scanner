# Standalone Atlassian Log Scanner/Analyser
 > Sick of the time it takes to scan your Atlassian log files using the Log Analyser? _Enter SALSA!_
 
[![Build Status](https://travis-ci.org/jackgraves/standalone-atlassian-log-scanner.svg?branch=master)](https://travis-ci.org/jackgraves/standalone-atlassian-log-scanner) [![Ask Me Anything !](https://img.shields.io/badge/Ask%20me-anything-1abc9c.svg)](https://community.atlassian.com/t5/Jira-discussions/An-Open-Source-Standalone-Log-Analyser-for-Atlassian-Products/td-p/794406) [![MIT license](https://img.shields.io/badge/License-MIT-blue.svg)](https://lbesson.mit-license.org/) [![Open Source Love svg2](https://badges.frapsoft.com/os/v2/open-source.svg?v=103)](https://github.com/ellerbrock/open-source-badges/)

This is a standalone version of the Log Scanner (Hercules) that is built into Jira, Confluence, Bitbucket, Bamboo, Crowd and Fisheye/Crucible.

The aim of this project is to increase the speed of scanning compared to executing through the interface of the Atlassian applications.

It also extends the provided tool with the following improvements:
* Allows third-party developers to provide definition files that can be used in addition to the Atlassian-supplied definitions
* Can be integrated with a support system to perform automated scanning on submission of log files
* Run the analysis on a different box than the Jira/Confluence system that is experiencing a problem
* Code can also be used for scanning any text files for Regular Expressions from XML.
* Run in Sequence or in Parrallel and display verbose information

## Performance
A 3MB Log File took the following duration:
* Support Tools Log Analyser: 6 minutes 14 seconds
* Standalone Log Scanner (sequential): 1 minute 48 seconds
* Standalone Log Scanner (parrallel): 34 seconds

*That's an over 70% improvement when sequentially run and an over 90% improvement when run in parrallel*

## How it Works
The following steps are followed:
1. (if it doesn't already exist) Download the definition file from the Atlassian website, or a custom URL
2. Parses the XML into JAXB Objects
3. Generates Regular Expression List
4. Reads the Log File
5. Runs Regular Expressions on each Log Line (either sequentially or in parallel)
6. Prints out the URL of all errors that have been found in the system (distinct, or all using verbose mode)

## Compiling
Run the following command to build the project into a JAR file:

`mvn package`

## Usage
Execute the following on your command line (CMD or Bash) and leave the `-stream` parameter out if you would like it to run sequentially (which is much less demanding). To see all problems, use the `-verbose` flag

`java -jar log-scanner.jar -def=jira-core -log=atlassian-jira.log -stream`

## Custom Definitions
This tool supports custom definitions, by providing a URL as the definition argument:

`-def=https://www.example.com/definition.xml`

There is a sample definition file in the examples/ folder.

## Download
You can download a pre-compiled binary from the [releases page](https://github.com/jackgraves/standalone-atlassian-log-scanner/releases)

## License
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fjackgraves%2Fstandalone-atlassian-log-scanner.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fjackgraves%2Fstandalone-atlassian-log-scanner?ref=badge_large)

## Example
### Built-in Tool

![Support Tools Output](example/screenshot-hercules.png)

### Standalone Tool

![SALSA Output](example/screenshot-salsa.png)

## Improvements
- [x] Use Parallel Streams API to speed up analysis
- [x] Implement flags and arguments
- [x] Show Datetime for each detected problem
- [ ] Show line number against each detected problem
- [ ] Implement multiple definitions per analysis (by combining XML)
- [ ] Implement Jira Service Desk App for use with support tickets
- [ ] Port to Node.js for use with NPM
