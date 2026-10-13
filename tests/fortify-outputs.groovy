import org.codehaus.groovy.control.CompilerConfiguration

class FlowInterruption extends RuntimeException {}

// Execute filesystem steps while simulating only Jenkins build/artifact services.
abstract class FlowHarness extends Script {
    File workspace
    File directory
    Map builds = [:]
    Map extraEnv = [:]
    List requested = []
    List copied = []
    List archived = []
    List problems = []
    boolean stageEnabled = true
    String result = 'SUCCESS'

    void pipeline(Closure body) { body() }
    void stages(Closure body) { body() }
    void options(Closure body) {}
    void agent(Object value) {}
    void stage(String name, Closure body) { stageEnabled = true; body() }
    void when(Closure body) { stageEnabled = body() }
    boolean expression(Closure body) { body() }
    void steps(Closure body) { if (stageEnabled) body() }
    void script(Closure body) { body() }
    void properties(List values) {}
    Map string(Map value) { value }
    Map choice(Map value) { value }
    Map booleanParam(Map value) { value }
    String specific(String number) { number }
    void echo(Object message) {}
    void error(String message) { throw new RuntimeException(message) }

    void catchError(Map settings, Closure body) {
        assert settings.buildResult == 'UNSTABLE'
        assert settings.catchInterruptions == false
        try {
            body()
        } catch (FlowInterruption failure) {
            throw failure
        } catch (RuntimeException failure) {
            result = 'UNSTABLE'
            problems.add(failure.message)
        }
    }

    Map build(Map settings) {
        requested.add(settings.job)
        assert settings.parameters.find { it.name == 'git_branch_name' }.value == 'master'
        assert settings.parameters.find { it.name == 'env' }.value == 'uat'
        Map build = builds[settings.job]
        assert build != null : "Unexpected job: ${settings.job}"
        if (build.interrupted) throw new FlowInterruption()
        if (settings.job != 'FORTIFY-RELEASE-FSAP-FLOW-UTILS') {
            assert settings.propagate == false
        } else if (build.result != 'SUCCESS') {
            error('Release failed')
        }
        return build
    }

    void copyArtifacts(Map settings) {
        Map build = builds[settings.projectName]
        assert settings.selector == build.number.toString()
        assert settings.filter == 'scan-output/**'
        copied.add([settings.projectName, settings.selector])
        if (build.copyFailure) error('Copy denied')
        build.files.each { path, contents ->
            File target = new File(directory, path)
            target.parentFile.mkdirs()
            target.text = contents
        }
    }

    void dir(String path, Closure body) {
        File previous = directory
        directory = new File(directory, path)
        directory.mkdirs()
        try { body() } finally { directory = previous }
    }

    void deleteDir() {
        directory.deleteDir()
        directory.mkdirs()
    }

    void withEnv(List values, Closure body) {
        Map previous = new LinkedHashMap(extraEnv)
        values.each { value ->
            int split = value.indexOf('=')
            extraEnv[value.substring(0, split)] = value.substring(split + 1)
        }
        try { body() } finally { extraEnv = previous }
    }

    void sh(String command) {
        ProcessBuilder builder = new ProcessBuilder('/bin/sh', '-c', command)
        builder.directory(directory).redirectErrorStream(true)
        builder.environment().putAll(extraEnv)
        Process process = builder.start()
        String output = process.inputStream.text
        if (process.waitFor() != 0) error("Shell failed: ${output}")
    }

    void archiveArtifacts(Map settings) {
        settings.artifacts.split(',').each { path ->
            assert new File(directory, path).length() > 0
            archived.add(path)
        }
    }
}

File repository = new File(args ? args[0] : '.').canonicalFile
List suffixes = ['OWASPTop10.pdf', 'OWASPTop10.html', 'DeveloperWorkbook.pdf', 'DeveloperWorkbook.html']
def outputs = { String name ->
    Map files = [:]
    suffixes.each { suffix -> files["scan-output/reports/${name}_${suffix}".toString()] = 'report' }
    files["scan-output/artifacts/module/build/libs/${name}.jar".toString()] = 'jar'
    files
}
def success = { String name, int number -> [number: number, result: 'SUCCESS', files: outputs(name)] }
int checks = 0

def exercise = { Map parameters, Map builds, Closure verify ->
    File workspace = File.createTempDir('fortify-flow-', '-test')
    try {
        // Seed stale data in both the parent and a neighboring project workspace.
        ['artifacts.tar.gz', 'reports.tar.gz', 'saved_reports/OLD/old.pdf',
         'saved_artifacts/OLD/old.jar', 'collected/OLD/scan-output/reports/old.pdf',
         'FORTIFY-OLD/build/libs/old.jar'].each { path ->
            File file = new File(workspace, path)
            file.parentFile.mkdirs()
            file.text = 'stale'
        }
        CompilerConfiguration configuration = new CompilerConfiguration(scriptBaseClass: FlowHarness.name)
        FlowHarness flow = (FlowHarness) new GroovyShell(FlowHarness.class.classLoader,
            new Binding([params: parameters, env: [:], any: 'any']), configuration)
            .parse(new File(repository, 'fortify/Jenkinsfile'))
        flow.setWorkspace(workspace)
        flow.setDirectory(workspace)
        flow.setBuilds(builds)
        flow.run()
        verify(flow, workspace)
        checks++
    } finally {
        workspace.deleteDir()
    }
}
def selected = [git_branch_name: 'master', is_release_utils: false, is_scan_ncb: true, is_scan_fac: true]
def tarEntries = { File workspace, String name ->
    Process process = new ProcessBuilder('tar', '-tzf', new File(workspace, name).path).start()
    String output = process.inputStream.text
    assert process.waitFor() == 0
    output
}
def expectNoArchives = { flow, workspace ->
    assert flow.archived.isEmpty()
    assert !new File(workspace, 'artifacts.tar.gz').exists()
    assert !new File(workspace, 'reports.tar.gz').exists()
}

exercise(selected, ['FORTIFY-FAC': success('FAC', 81), 'FORTIFY-NCB': success('NCB', 37)]) { flow, workspace ->
    assert flow.result == 'SUCCESS'
    assert flow.copied == [['FORTIFY-FAC', '81'], ['FORTIFY-NCB', '37']]
    assert flow.archived == ['artifacts.tar.gz', 'reports.tar.gz']
    ['artifacts.tar.gz', 'reports.tar.gz'].each { name ->
        String entries = tarEntries(workspace, name)
        assert entries.contains('FORTIFY-NCB/') && entries.contains('FORTIFY-FAC/')
        assert !entries.contains('OLD')
    }
}
exercise(selected, ['FORTIFY-FAC': [number: 82, result: 'FAILURE'], 'FORTIFY-NCB': success('NCB', 38)]) { flow, workspace ->
    assert flow.result == 'UNSTABLE'
    assert flow.copied == [['FORTIFY-NCB', '38']]
    assert flow.requested == ['FORTIFY-FAC', 'FORTIFY-NCB']
    assert !tarEntries(workspace, 'reports.tar.gz').contains('FORTIFY-FAC/')
}
['missingReport', 'missingArtifact', 'emptyArtifact', 'duplicateReport', 'copyFailure'].each { failure ->
    Map broken = success('FAC', 83)
    if (failure == 'missingReport') broken.files.remove('scan-output/reports/FAC_DeveloperWorkbook.html')
    if (failure == 'missingArtifact') broken.files.remove('scan-output/artifacts/module/build/libs/FAC.jar')
    if (failure == 'emptyArtifact') broken.files['scan-output/artifacts/module/build/libs/FAC.jar'] = ''
    if (failure == 'duplicateReport') broken.files['scan-output/reports/OTHER_OWASPTop10.pdf'] = 'report'
    if (failure == 'copyFailure') broken.copyFailure = true
    exercise(selected, ['FORTIFY-FAC': broken, 'FORTIFY-NCB': success('NCB', 39)]) { flow, workspace ->
        assert flow.result == 'UNSTABLE'
        assert !tarEntries(workspace, 'reports.tar.gz').contains('FORTIFY-FAC/')
        assert tarEntries(workspace, 'artifacts.tar.gz').contains('FORTIFY-NCB/')
    }
}
exercise(selected, ['FORTIFY-FAC': [number: 84, result: 'FAILURE'], 'FORTIFY-NCB': [number: 40, result: 'ABORTED']]) { flow, workspace ->
    assert flow.result == 'UNSTABLE'
    assert flow.copied.isEmpty()
    expectNoArchives(flow, workspace)
}
exercise([git_branch_name: 'master', is_release_utils: false], [:]) { flow, workspace ->
    assert flow.result == 'SUCCESS'
    assert flow.requested.isEmpty()
    expectNoArchives(flow, workspace)
}
exercise(selected + [is_release_utils: true], ['FORTIFY-RELEASE-FSAP-FLOW-UTILS': [result: 'FAILURE'],
    'FORTIFY-FAC': success('FAC', 85), 'FORTIFY-NCB': success('NCB', 41)]) { flow, workspace ->
    assert flow.result == 'UNSTABLE'
    assert flow.archived.size() == 2
}
try {
    exercise(selected, ['FORTIFY-FAC': [interrupted: true]]) { flow, workspace -> assert false }
    assert false : 'Parent cancellation must propagate'
} catch (FlowInterruption expected) {
    checks++
}

def collect = { Map files, boolean shouldSucceed, Closure verify ->
    File workspace = File.createTempDir('fortify-collector-', '-test')
    try {
        files.each { path, contents ->
            File target = new File(workspace, path)
            target.parentFile.mkdirs()
            target.text = contents
        }
        Process process = new ProcessBuilder('/bin/sh',
            new File(repository, 'fortify/shells/Collect-Fortify-Outputs.sh').path)
            .directory(workspace).redirectErrorStream(true).start()
        String output = process.inputStream.text
        assert (process.waitFor() == 0) == shouldSucceed : output
        verify(workspace)
        checks++
    } finally {
        workspace.deleteDir()
    }
}
Map reports = [:]
suffixes.each { suffix -> reports["scan-output/reports/NCB_${suffix}".toString()] = 'report' }
collect(reports + ['module one/build/libs/app.jar': 'jar1', 'module two/build/libs/app.jar': 'jar2',
    'web/build/libs/app.war': 'war', 'libs/dependency.jar': 'dependency',
    'gradle/wrapper/gradle-wrapper.jar': 'wrapper', 'scan-output/artifacts/old.jar': 'stale'], true) { workspace ->
    assert new File(workspace, 'scan-output/artifacts/module one/build/libs/app.jar').text == 'jar1'
    assert new File(workspace, 'scan-output/artifacts/module two/build/libs/app.jar').text == 'jar2'
    assert new File(workspace, 'scan-output/artifacts/web/build/libs/app.war').text == 'war'
    assert !new File(workspace, 'scan-output/artifacts/libs/dependency.jar').exists()
    assert !new File(workspace, 'scan-output/artifacts/old.jar').exists()
}
collect(reports + ['scan-output/artifacts/old.jar': 'stale'], false) { workspace -> }
collect(reports + ['module/build/libs/app.jar': ''], false) { workspace -> }
collect(['module/build/libs/app.jar': 'jar'], false) { workspace -> }
Map incomplete = new LinkedHashMap(reports)
incomplete['scan-output/reports/NCB_DeveloperWorkbook.html'] = ''
collect(incomplete + ['module/build/libs/app.jar': 'jar'], false) { workspace -> }

// Run the real report-generation block without invoking Fortify SCA or Gradle.
new File(repository, 'fortify/shells').listFiles()
    .findAll { it.name.startsWith('Fortify-') && it.name.endsWith('.sh') }
    .each { shell ->
        String source = shell.text
        String buildId = (source =~ /(?m)^BUILDID="([^"]+)"$/)[0][1]
        int start = source.indexOf('echo Starting generate reports')
        int end = source.indexOf('# ###########################################################################', start)
        assert start >= 0 && end > start
        String reportBlock = source.substring(start, end)
        ['success', 'generatorFailure', 'copyFailure'].each { scenario ->
            File workspace = File.createTempDir('fortify-report-', '-test')
            try {
                File binaries = new File(workspace, 'bin')
                binaries.mkdirs()
                File generator = new File(binaries, 'BIRTReportGenerator')
                generator.text = '''#!/bin/sh
set -eu
output=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        -output) output=$2; shift 2 ;;
        *) shift ;;
    esac
done
if [ "${REPORT_TEST_FAILURE:-}" = 'generatorFailure' ]; then
    case "$output" in *_DeveloperWorkbook.html) exit 7 ;; esac
fi
printf 'new report' > "$output"
'''
                assert generator.setExecutable(true)
                File reportDir = new File(workspace, 'current reports')
                File historyDir = new File(workspace, 'historical reports')
                reportDir.mkdirs()
                if (scenario != 'copyFailure') historyDir.mkdirs()
                ProcessBuilder builder = new ProcessBuilder('/bin/sh', '-c', reportBlock)
                builder.directory(workspace).redirectErrorStream(true)
                builder.environment().putAll([
                    PATH: binaries.path + ':' + System.getenv('PATH'),
                    BUILDID: buildId, FPR: new File(workspace, 'current.fpr').path,
                    REPORT_OUTPUT: reportDir.path, REPORT_PATH: historyDir.path,
                    REPORT_TEST_FAILURE: scenario,
                ])
                Process process = builder.start()
                String output = process.inputStream.text
                assert (process.waitFor() == 0) == (scenario == 'success') : "${shell.name}: ${output}"
                if (scenario == 'success') {
                    suffixes.each { suffix ->
                        assert new File(reportDir, "${buildId}_${suffix}").text == 'new report'
                        assert new File(historyDir, "${buildId}_${suffix}").text == 'new report'
                    }
                }
                checks++
            } finally {
                workspace.deleteDir()
            }
        }
    }

// Compile both Groovy entry points; the harness does not emulate Jenkins CPS/plugins.
GroovyClassLoader compiler = new GroovyClassLoader()
compiler.parseClass(new File(repository, 'vars/fortifyScan.groovy'))
println "PASS: ${checks} flow/collection/report scenarios; Groovy entry points compiled"
