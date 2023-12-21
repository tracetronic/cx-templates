/*
 * Copyright (c) 2021 - 2023 tracetronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */

/**
 * Load your Jenkins Shared Library here.
 * For more information see https://www.jenkins.io/doc/book/pipeline/shared-libraries/
 */
@Library('shared-lib@master')
/**
 * Load the tracetronic Jenkins Library to use all provided helper methods. This library can be defined in Jenkins global settings as well.
 * For more information see https://www.jenkins.io/doc/book/pipeline/shared-libraries/
 */
@Library('github.com/tracetronic/jenkins-library@main')

// Import your classes here.
import com.mycorp.pipeline.somelib.UsefulClass

// Define global variables that can be accessed from anywhere.
def builder
def tester
def publisher
def doc
def deployer
def profile
String timeoutUnit = 'MINUTES'

// Define your declarative pipeline here.
pipeline {
    /**
     * Declare directive blocks here.
     * For more information see https://www.jenkins.io/doc/book/pipeline/syntax/#declarative-directives
     */
    triggers {
        // Triggers the job each night between 3-5 am.
        cron('H H(3-5) * * *')
        // Polls the SCM system every five minutes for changes.
        pollSCM('H/5 * * * *')
    }
    options {
        timestamps()
        timeout(time: 60, unit: timeoutUnit)
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    parameters{
        string(name: 'branch', defaultValue: 'master', description: 'A specific branch to check out.')
        booleanParam(name: 'integrationTest', defaultValue: true,
            description: 'Determines whether the integration tests should be executed or not.')
    }

    /**
     * Declare sections blocks here.
     * For more information see https://www.jenkins.io/doc/book/pipeline/syntax/#declarative-sections
     */
    agent any
    stages {
        stage('Check out from SCM') {
            steps {
                git url: 'git@repo.mycorp.com.git', branch: params.branch

                script {
                    // Load checked out utility scripts
                    builder = load 'scripts/build.groovy'
                    tester = load 'scripts/test.groovy'
                    publisher = load 'scripts/publish.groovy'
                    doc = load 'scripts/documentation.groovy'
                    deployer = load 'scripts/deploy.groovy'
                }
            }
        }
        stage('Run Software Build') {
            steps {
                script {
                    // Using vars of loaded Jenkins Shared Library at line 5
                    log.info('Running the software build via checked out script.')

                    profile = builder.profile
                    builder.build(profile)
                }
            }
        }
        stage('Test Software Build') {
            steps {
                script {
                    log.info('Running the unit tests on preceded build artifact.')

                    tester.runUnitTests()
                    if (params.integrationTest) {
                        log.info('Running the integration tests triggered via integration parameter.')
                        tester.runIntegrationTests()
                    }
                }
            }
        }
        stage('Build Software Documentation') {
            // Build documentation on nightly builds only
            when {
                triggeredBy 'TimerTrigger'
            }
            steps {
                script {
                    log.info('Generate the software documentation.')
                    doc.createDocumentation('/documentation')
                }
            }
        }
        stage('Upload Artifacts') {
            steps {
                script {
                    log.info('Upload the build artifacts to Artifactory.')
                    publisher.toArtifactory('https://artifactory.mycorp.com', profile)
                }
            }
        }
        stage('Deploy Software') {
            steps {
                script {
                    log.info('Deploy the software build to the production system.')
                    timeout(time: 30, unit: timeoutUnit) {
                        // All executions inside are timeout related
                        deployer.toProduction(profile)
                    }
                }
            }
        }
    }
    post {
        always {
            /**
             * Generates a test.guide compatible JSON report of a pipeline build including logs and stage meta data.
             * For more information see https://github.com/tracetronic/jenkins-library
             */
            pipeline2ATX(true)
        }
        failure {
            /**
             * Using email fast feedback in case of an failure.
             * For more information see https://plugins.jenkins.io/email-ext/
             */
            mail to: 'dev-team@mycorp.com',
                subject: "Failed Pipeline: '${env.JOB_NAME} #${env.BUILD_NUMBER}'",
                body: "Check console output at <a href='${env.BUILD_URL}'>${env.JOB_NAME} #${env.BUILD_NUMBER}</a>"
        }
    }
}
