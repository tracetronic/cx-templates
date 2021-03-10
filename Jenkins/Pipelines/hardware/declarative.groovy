/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */

/**
 * Load your Jenkins Shared Library here.
 * For more information see https://www.jenkins.io/doc/book/pipeline/shared-libraries/
 */
@Library('shared-lib@master')
/**
 * Load the TraceTronic Jenkins Library to use all provided helper methods. This library can be defined in Jenkins global settings as well.
 * For more information see https://www.jenkins.io/doc/book/pipeline/shared-libraries/
 */
@Library('github.com/tracetronic/jenkins-library@main')

// Import your classes here.
import com.mycorp.pipeline.somelib.UsefulClass

// Define global variables that can be accessed from anywhere
def hilConfig = readYaml text: params.hilConfig
int buildTimeout = hilConfig.timeout
String ecuTestVersion = hilConfig.etVersion

// Using vars of loaded Jenkins Shared Library at line 5
def nodeList = getNodesWithLabel(hilConfig.nodeLabels)

// Define your declarative pipeline here.
pipeline {
    /**
     * Declare directive blocks here.
     * For more information see https://www.jenkins.io/doc/book/pipeline/syntax/#declarative-directives
     */
    triggers {
        // Triggers the job each night between 3-5 am.
        cron('H H(3-5) * * *')
    }
    options {
        timestamps()
        timeout(time: buildTimeout, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    parameters {
        string(name: 'hilConfig', description: 'Load HiL configuration from YAML definition.',
            defaultValue: '{"timeout": "60", "etVersion": "2021.1", "nodeLabels": ["HiL_A", "HiL_B"]}, ...')
        string(name: 'branch', defaultValue: 'master', description: 'A specific branch to check out.')
        booleanParam(name: 'debug', defaultValue: false, description: 'Enable debug information.')
    }

    /**
     * Declare sections blocks here.
     * For more information see https://www.jenkins.io/doc/book/pipeline/syntax/#declarative-sections
     */
    agent any
    stages {
        stage('Check Node Availability') {
            steps {
                script {
                    if (!nodeList) {
                        error('No nodes found with defined HiL test configuration.')
                    }
                    log.info("Found ${nodeList.size()} available node(s): ${nodeList}")
                }
            }
        }
        stage('Clean up HiL environment') {
            steps {
                // Using vars of loaded Jenkins Shared Library at line 5
                log.info('Trying to stop all testbench tools...')

                // Terminate all running tool instances (e.g. INCA, CANoe, ...)
                warnError(message: 'Terminating testbench tools failed!',
                    buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    // Using vars of loaded Jenkins Shared Library at line 5
                    stopTools(hilConfig.tools)
                }

                // Clean the whole workspace
                cleanWS()
            }
        }
        stage('Check out from SCM') {
            steps {
                git url: 'git@repo.mycorp.com.git', branch: params.branch
            }
        }
        stage('Set up HiL environment') {
            steps {
                log.info('Prepare the test bench to run all tests.')

                /**
                 * Start tools utilizing ECU-TEST Jenkins plugin
                 * For more information see https://plugins.jenkins.io/ecutest/
                 */
                startET toolName: ecuTestVersion, workspaceDir: hilConfig.etWsPath
                startTS ecuTestVersion

                script {
                    if (hilConfig.init) {
                        log.info('Run HiL initialization routine.')
                        testProject testFile: hilConfig.initProject,
                            testConfig: [tbcFile: hilConfig.tbc, tcfFile: hilConfig.tcf]
                    } else {
                        log.info('HiL initialization was skipped.')
                    }
                }
            }
        }
        stage('Flash SUT') {
            steps {
                log.info('Flash system under test.')
                warnError(message: 'Flashing SUT failed!',
                    buildResult: 'FAILURE', stageResult: 'FAILURE') {
                        // Using vars of loaded Jenkins Shared Library at line 5
                        flashEcu(hilConfig.flash.config))
                    }
            }
        }
        stage('Test Execution') {
            steps {
                log.info('Run all tests via ECU-TEST plugin.')
                testFolder testFile: 'tests', recursiveScan: true,
                    testConfig: [tbcFile: hilConfig.tbc, tcfFile: hilConfig.tcf]
            }
        }
        stage('Publish Reports') {
            steps {
                log.info('Generate and publish test reports.')
                publishGenerators generators: [[name: 'JSON']], toolName: ecuTestVersion
                publishATX atxName: 'TEST-GUIDE'
            }
        }
        stage('Tear down HiL environment') {
            steps {
                log.info('Shut down the loaded environment.')
                stopET ecuTestVersion
                stopTS ecuTestVersion
                warnError(message: 'Shutting down the loaded environment was not performed successfully',
                    buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    // Using vars of loaded Jenkins Shared Library at line 5
                    shutDownEnv(hilConfig))
                }
            }
        }
    }
    post {
        always {
            /**
             * Generates a TEST-GUIDE compatible JSON report of a pipeline build including logs and stage meta data.
             * For more information see https://github.com/tracetronic/jenkins-library
             */
            pipeline2ATX(params.debug)
        }
        failure {
            /**
             * Using email fast feedback in case of an failure.
             * For more information see https://plugins.jenkins.io/email-ext/
             */
            mail to: 'hil-team@mycorp.com',
                subject: "Failed Pipeline: '${env.JOB_NAME} #${env.BUILD_NUMBER}'",
                body: "Check console output at <a href='${env.BUILD_URL}'>${env.JOB_NAME} #${env.BUILD_NUMBER}</a>"
        }
    }
}
