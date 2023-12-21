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
                // Triggers the job each night between 3-5 am.
                cron('H H(3-5) * * *'),
                // Polls the SCM system every five minutes for changes.
                pollSCM('H/5 * * * *')
            ]
        ),
        parameters(
            [
                string(name: 'branch', defaultValue: 'master', description: 'A specific branch to check out.'),
                booleanParam(name: 'integrationTest', defaultValue: true,
                    description: 'Determines whether the integration tests should be executed or not.')
            ]
        )
    ]
)

try {
    node {
        // Define global variables that can be accessed from anywhere.
        def builder
        def tester
        def publisher
        def doc
        def deployer
        def profile
        String timeoutUnit = 'MINUTES'

        timestamps {
            stage('Check out from SCM') {
                git url: 'git@repo.mycorp.com.git', branch: params.branch

                // Load checked out utility scripts
                builder = load 'scripts/build.groovy'
                tester = load 'scripts/test.groovy'
                publisher = load 'scripts/publish.groovy'
                doc = load 'scripts/documentation.groovy'
                deployer = load 'scripts/deploy.groovy'
            }
            stage('Run Software Build') {
                // Using vars of loaded Jenkins Shared Library at line 5
                log.info('Running the software build via checked out script.')

                profile = builder.profile
                builder.build(profile)
            }
            stage('Test Software Build') {
                log.info('Running the unit tests on preceded build artifact.')

                tester.runUnitTests()
                if (params.integrationTest) {
                    log.info('Running the integration tests triggered via integration parameter.')
                    tester.runIntegrationTests()
                }
            }
            stage('Build Software Documentation') {
                // Build documentation on nightly builds only
                if (currentBuild.getBuildCauses('hudson.triggers.TimerTrigger.TimerTriggerCause')) {
                    log.info('Generate the software documentation.')
                    doc.createDocumentation('/documentation')
                }
            }
            stage('Upload Artifacts') {
                log.info('Upload the build artifacts to Artifactory.')
                publisher.toArtifactory('https://artifactory.mycorp.com', profile)
            }
            stage('Deploy Software') {
                log.info('Deploy the software build to the production system.')
                timeout(time: 30, unit: timeoutUnit) {
                    // All executions inside are timeout related
                    deployer.toProduction(profile)
                }
            }
        }
    }
} catch (exc) {
    /**
    * Using email fast feedback in case of an failure.
    * For more information see https://plugins.jenkins.io/email-ext/
    */
    mail to: 'dev-team@mycorp.com',
        subject: "Failed Pipeline: '${env.JOB_NAME} #${env.BUILD_NUMBER}'",
        body: """Check console output at <a href='${env.BUILD_URL}'>${env.JOB_NAME} #${env.BUILD_NUMBER}</a><br>
                 Stacktrace: ${exc}"""

} finally {
    /**
     * Generates a test.guide compatible JSON report of a pipeline build including logs and stage meta data.
     * For more information see https://github.com/tracetronic/jenkins-library
     */
    node {
        pipeline2ATX(true)
    }
}
