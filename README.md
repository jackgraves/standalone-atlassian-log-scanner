# Standalone Atlassian Log Scanner
This is a standalone version of the Log Scanner (Hercules) that is built into Jira, Confluence, Bitbucket, Bamboo, Crowd and Fisheye/Crucible. 
The aim is to increase the speed of scanning dramatically when executed through the interface.

## How it Works
The following steps are followed:
1. (if it doesn't already exist) Download the definition file from the Atlassian website
2. Parses the XML into JAXB Objects
3. Generates Regular Expression List
4. Reads the Log File
5. Runs Regular Expressions on each Log Line
6. Prints out the URL of all errors that have been found in the system (distinct)

## Compiling
Run the following command to build the project into a JAR file:
`mvn package`

## Usage
Execute the following on your command line (CMD or Bash):
`java -jar logscanner.jar "jira-core" "atlassian-jira.log"`
