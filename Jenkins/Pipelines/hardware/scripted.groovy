/*
 * Copyright (c) 2021 - 2023 tracetronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */

/**
 * Load your Jenkins Shared Library here.
 * For more information see https://www.jenkins.io/doc/book/pipeline/shared-libraries/
 */
@Library('shared-lib@master') _
/**
 * Load the tracetronic Jenkins Library to use all provided helper methods. This library can be defined in Jenkins global settings as well.
 * For more information see https://www.jenkins.io/doc/book/pipeline/shared-libraries/
 */
@Library('github.com/tracetronic/jenkins-library@main')

// Import your classes here.
import com.mycorp.pipeline.somelib.UsefulClass

/**
 * Define job properties here.
 * For more information see https://www.jenkins.io/doc/pipeline/steps/workflow-multibranch/#properties-set-job-properties
 */
properties(
    [
        [
            $class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']
        ],
        pipelineTriggers(
            [
                // Triggers the job each night one time between 3-5 am.
                cron('H H(3-5) * * *')
            ]
        ),
        parameters(
            [
                string(name: 'hilConfig', description: 'Load HiL configuration from YAML definition.',
                    defaultValue: '{"timeout": "60", "etVersion": "2021.1", "nodeLabels": ["HiL_A", "HiL_B"]}, ...')
                string(name: 'branch', defaultValue: 'master', description: 'A branch to check out'),
                booleanParam(name: 'debug', defaultValue: false, description: 'Get debug information.'),
            ]
        )
    ]
)

// Define global variables that can be accessed from anywhere
def hilConfig = readYaml text: params.hilConfig
int buildTimeout = hilConfig.timeout
String ecutestVersion = hilConfig.etVersion

// Using vars of loaded Jenkins Shared Library at line 5
def nodeList = getNodesWithLabel(hilConfig.nodeLabels)

if (!nodeList) {
    error('No nodes found with defined HiL test configuration.')
    return
}
log.info("Found ${nodeList.size()} available node(s): ${nodeList}")

try {
    timeout(time: buildTimeout, unit: 'MINUTES') {
        node(nodeList) {
            timestamps {
                stage('Prepare HiL Environment') {
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
                stage('Check out from SCM') {
                    git url: 'git@repo.mycorp.com.git', branch: params.branch

                }
                stage('Set up HiL environment') {
                    log.info('Prepare the test bench to run all tests.')

                    /**
                    * Start tools utilizing ecu.test Jenkins plugin
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
                stage('Flash SUT') {
                    log.info('Flash system under test.')
                    warnError(message: 'Flashing SUT failed!',
                    buildResult: 'FAILURE', stageResult: 'FAILURE') {
                        // Using vars of loaded Jenkins Shared Library at line 5
                        flashEcu(hilConfig.flash.config))
                    }
                }
                stage('Test Execution') {
                    log.info('Run all tests via ecu.test plugin.')
                    testFolder testFile: 'tests', recursiveScan: true,
                        testConfig: [tbcFile: hilConfig.tbc, tcfFile: hilConfig.tcf]
                }
                stage('Publish Reports') {
                    log.info('Generate and publish test reports.')
                    publishGenerators generators: [[name: 'JSON']], toolName: ecuTestVersion
                    publishATX atxName: 'test.guide'
                }
                stage('Shut Down') {
                    log.info('Shut down the loaded environment.')
                    stopET ecuTestVersion
                    stopTS ecuTestVersion
                    warnError(message: 'Shutting down the loaded environment was not performed successfully',
                        buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                        // Using vars of loaded Jenkins Shared Library at line 5
                        shutDownEnv(hilConfig))
                    }
                }
                stage('Pipeline 2 test.guide Json') {
                    /**
                    * generate a  test.guide json report schema conform zip of the pipeline build
                    * For more information see https://github.com/tracetronic/jenkins-library
                    */
                    pipeline2AtXGenerator(true)
                }
            }
        }
    }
} catch (exc) {
    /**
    * Using email fast feedback in case of an failure.
    * For more information see https://plugins.jenkins.io/email-ext/
    */
    mail to: 'hil-team@mycorp.com',
        subject: "Failed Pipeline: '${env.JOB_NAME} #${env.BUILD_NUMBER}'",
        body: """Check console output at <a href='${env.BUILD_URL}'>${env.JOB_NAME} #${env.BUILD_NUMBER}</a><br>
                 Stacktrace: ${exc}"""
} finally {
    /**
     * Generates a test.guide compatible JSON report of a pipeline build including logs and stage meta data.
     * For more information see https://github.com/tracetronic/jenkins-library
     */
    pipeline2ATX(params.debug)
}
