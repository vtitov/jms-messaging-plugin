package com.redhat.jenkins.plugins.ci.integration;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.hasContent;

import java.io.IOException;
import java.io.StringWriter;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jenkinsci.test.acceptance.docker.Docker;
import org.jenkinsci.test.acceptance.docker.DockerContainer;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.JenkinsLogger;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.StringParameter;
import org.jenkinsci.test.acceptance.po.WorkflowJob;

import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger.MsgCheck;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger.ProviderData;
import com.redhat.jenkins.plugins.ci.integration.po.CINotifierBuildStep;
import com.redhat.jenkins.plugins.ci.integration.po.CINotifierPostBuildStep;
import com.redhat.jenkins.plugins.ci.integration.po.CISubscriberBuildStep;

/**
 * Created by shebert on 06/06/17.
 */
public class SharedMessagingPluginIntegrationTest extends AbstractJUnitTest {

    public static String MESSAGE_CHECK_FIELD = "content";
    public static String MESSAGE_CHECK_VALUE = "catch me";
    public static String MESSAGE_CHECK_CONTENT = "{ \"" + MESSAGE_CHECK_FIELD + "\" : \"" + MESSAGE_CHECK_VALUE + "\" }";

    public void _testAddDuplicateMessageProvider() throws IOException {
        elasticSleep(5000);
        jenkins.save();
        assertThat(driver, hasContent("Attempt to add a duplicate JMS Message Provider - test"));
    }

    public void _testAddQueueMessageProvider() throws IOException {
        elasticSleep(5000);
        jenkins.save();
        // Not sure what else to do here....
    }

    public void _testSimpleCIEventSubscribe() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        subscriber.selector.set("CI_TYPE = 'code-quality-checks-done'");
        subscriber.variable.set("HELLO");

        jobA.addShellStep("echo $HELLO");
        jobA.save();
        jobA.scheduleBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("Hello World");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("Hello World"));

    }

    public void _testSimpleCIEventSubscribeWithCheck() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        com.redhat.jenkins.plugins.ci.integration.po.CISubscriberBuildStep.MsgCheck check = subscriber.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        subscriber.variable.set("HELLO");

        jobA.addShellStep("echo $HELLO");
        jobA.save();
        jobA.scheduleBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("catch me"));

    }

    public void _testSimpleCIEventSubscribeWithTopicOverride() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();

        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        subscriber.overrides.check();
        subscriber.topic.set("otopic");
        subscriber.selector.set("CI_TYPE = 'code-quality-checks-done'");
        subscriber.variable.set("MESSAGE_CONTENT");

        jobA.addShellStep("echo $MESSAGE_CONTENT");
        jobA.save();
        elasticSleep(1000);
        jobA.scheduleBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);

        notifier.overrides.check();
        notifier.topic.set("otopic");
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageContent.set("This is my content");

        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("This is my content"));
    }

    public void _testSimpleCIEventSubscribeWithCheckWithTopicOverride() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();

        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        subscriber.overrides.check();
        subscriber.topic.set("otopic");
        com.redhat.jenkins.plugins.ci.integration.po.CISubscriberBuildStep.MsgCheck check = subscriber.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        subscriber.variable.set("MESSAGE_CONTENT");

        jobA.addShellStep("echo $MESSAGE_CONTENT");
        jobA.save();
        elasticSleep(1000);
        jobA.scheduleBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);

        notifier.overrides.check();
        notifier.topic.set("otopic");
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);

        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("catch me"));
    }

    public void _testSimpleCIEventSubscribeWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        StringParameter p = jobA.addParameter(StringParameter.class);
        p.setName("MY_TOPIC");
        p.setDefault("my-topic");

        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        subscriber.overrides.check();
        subscriber.topic.set("$MY_TOPIC");
        subscriber.selector.set("CI_TYPE = 'code-quality-checks-done'");
        subscriber.variable.set("HELLO");

        jobA.addShellStep("echo $HELLO");
        jobA.save();
        jobA.scheduleBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("my-topic");
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("Hello World");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("Hello World"));
    }

    public void _testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        StringParameter p = jobA.addParameter(StringParameter.class);
        p.setName("MY_TOPIC");
        p.setDefault("my-topic");

        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        subscriber.overrides.check();
        subscriber.topic.set("$MY_TOPIC");
        com.redhat.jenkins.plugins.ci.integration.po.CISubscriberBuildStep.MsgCheck check = subscriber.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        subscriber.variable.set("HELLO");

        jobA.addShellStep("echo $HELLO");
        jobA.save();
        jobA.scheduleBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("my-topic");
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("catch me"));
    }

    public void _testSimpleCIEventTriggerWithPipelineSendMsg() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();

        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);
        job.script.set("node('master') {\n def message = sendCIMessage " +
                " providerName: 'test', " +
                " messageContent: '', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'}\n");
        job.save();
        job.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
    }

    public void _testSimpleCIEventTriggerWithCheckWithPipelineSendMsg() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        MsgCheck check = pd.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        jobA.save();

        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);
        job.script.set("node('master') {\n def message = sendCIMessage " +
                " providerName: 'test', " +
                " messageContent: '" + MESSAGE_CHECK_CONTENT + "'}\n");
        job.save();
        job.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
    }

    public void _testSimpleCIEventTrigger() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
    }

    public void _testSimpleCIEventTriggerWithCheck() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo job ran");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        CIEventTrigger.MsgCheck check = pd.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo job ran"));
    }

    public void _testSimpleCIEventTriggerWithCheckNoSquash() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("sleep 3;");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.noSquash.check();
        ProviderData pd = ciEvent.addProviderData();
        CIEventTrigger.MsgCheck check = pd.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();
        jobB.startBuild().shouldSucceed();
        jobB.startBuild().shouldSucceed();
        jobB.startBuild().shouldSucceed();
        jobB.startBuild().shouldSucceed();

        elasticSleep(20000);
        assertThat(jobA.getLastBuild().getNumber(), is(equalTo(5)));
    }

    public void _testSimpleCIEventTriggerWithWildcardInSelector() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("compose LIKE '%compose_id\": \"Fedora-Atomic%'");
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed\n " +
                "compose = \"compose_id\": \"Fedora-Atomic-25-20170105.0\"");
        notifier.messageContent.set("");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
    }

    public void _testSimpleCIEventTriggerWithRegExpCheck() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo job ran");
        jobA.addShellStep("echo CI_MESSAGE = $CI_MESSAGE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        CIEventTrigger.MsgCheck check = pd.addMsgCheck();
        check.field.set("$.compose.compose_id");
        check.expectedValue.set("Fedora-Atomic.+");
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageContent.set("{ \"compose\": { \"compose_id\": \"Fedora-Atomic-25-20170105.0\" } }");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo job ran"));
    }

    public void _testSimpleCIEventTriggerWithTopicOverride() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        pd.overrides.check();
        pd.topic.set("otopic");
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("otopic");
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
    }

    public void _testSimpleCIEventTriggerWithCheckWithTopicOverride() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo job ran");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        MsgCheck check = pd.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        pd.overrides.check();
        pd.topic.set("otopic");
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("otopic");
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo job ran"));
    }

    public void _testSimpleCIEventTriggerWithCheckWithTopicOverrideAndRestart() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo job ran");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        MsgCheck check = pd.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        pd.overrides.check();
        pd.topic.set("otopic");
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        jenkins.restart();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("otopic");
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo job ran"));
}

    public void _testSimpleCIEventTriggerWithMultipleTopics() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo $CI_MESSAGE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);

        ProviderData pd = ciEvent.addProviderData();
        pd.overrides.check();
        pd.topic.set("topic1");
        MsgCheck check = pd.addMsgCheck();
        check.field.set("my-topic");
        check.expectedValue.set("topic1");

        pd = ciEvent.addProviderData();
        pd.overrides.check();
        pd.topic.set("topic2");
        check = pd.addMsgCheck();
        check.field.set("my-topic");
        check.expectedValue.set("topic2");

        jobA.save();

        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierBuildStep notifier1 = jobB.addBuildStep(CINotifierBuildStep.class);
        notifier1.overrides.check();
        notifier1.topic.set("topic1");
        notifier1.messageContent.set("{ \"my-topic\" : \"topic1\" }");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("topic1"));

        FreeStyleJob jobC = jenkins.jobs.create();
        jobC.configure();
        CINotifierBuildStep notifier2 = jobC.addBuildStep(CINotifierBuildStep.class);
        notifier2.overrides.check();
        notifier2.topic.set("topic2");
        notifier1.messageContent.set("{ \"my-topic\" : \"topic2\" }");
        jobC.save();
        jobC.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("topic2"));
    }

    public void _testSimpleCIEventTriggerWithTopicOverrideAndVariableTopic() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.overrides.check();
        pd.topic.set("org.fedoraproject.my-topic");
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        StringParameter p = jobB.addParameter(StringParameter.class);
        p.setName("MY_TOPIC");
        p.setDefault("org.fedoraproject.my-topic");

        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("$MY_TOPIC");
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
    }

    public void _testSimpleCIEventTriggerWithCheckWithTopicOverrideAndVariableTopic() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo job ran");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.overrides.check();
        pd.topic.set("org.fedoraproject.my-topic");
        MsgCheck check = pd.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        StringParameter p = jobB.addParameter(StringParameter.class);
        p.setName("MY_TOPIC");
        p.setDefault("org.fedoraproject.my-topic");

        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("$MY_TOPIC");
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo job ran"));
    }

    public void _testSimpleCIEventTriggerWithParamOverride() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done'");
        StringParameter ciStatusParam = jobA.addParameter(StringParameter.class);
        ciStatusParam.setName("PARAMETER");
        ciStatusParam.setDefault("bad parameter value");
        StringParameter jenkinsStatusParam = jobA.addParameter(StringParameter.class);
        jenkinsStatusParam.setName("status");
        jenkinsStatusParam.setDefault("unknown status");

        jobA.addShellStep("echo $PARAMETER");
        jobA.addShellStep("echo $CI_MESSAGE");
        jobA.addShellStep("echo status::$status");
        jobA.save();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);

        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("PARAMETER = my parameter\nstatus=${BUILD_STATUS}\nCOMPOUND = Z${PARAMETER}Z");
        notifier.messageContent.set("This is my content with ${COMPOUND} ${BUILD_STATUS}");

        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("status::SUCCESS"));
        assertThat(jobA.getLastBuild().getConsole(), containsString("my parameter"));
        assertThat(jobA.getLastBuild().getConsole(),
                containsString("This is my content with Zmy parameterZ SUCCESS"));
    }

    public void _testSimpleCIEventTriggerHeadersInEnv(Job jobB, String expected) {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done'");

        // We are only checking that this shows up in the console output.
        jobA.addShellStep("echo $MESSAGE_HEADERS");
        jobA.save();

        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);

        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageContent.set("some irrelevant content");

        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString(expected));
    }

    public void _testSimpleCIEventSubscribeWithNoParamOverride() {
        // Job parameters are NOT overridden when the subscribe build step is used.
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();

        StringParameter ciStatusParam = jobA.addParameter(StringParameter.class);
        ciStatusParam.setName("PARAMETER");
        ciStatusParam.setDefault("original parameter value");

        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        subscriber.selector.set("CI_TYPE = 'code-quality-checks-done'");
        subscriber.variable.set("MESSAGE_CONTENT");

        jobA.addShellStep("echo $PARAMETER");
        jobA.addShellStep("echo $MESSAGE_CONTENT");
        jobA.save();
        elasticSleep(1000);
        jobA.scheduleBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);

        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("PARAMETER = my parameter");
        notifier.messageContent.set("This is my content");

        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("original parameter value"));
        assertThat(jobA.getLastBuild().getConsole(), containsString("This is my content"));
    }

    public void _testSimpleCIEventTriggerOnPipelineJob() {
        WorkflowJob jobA = jenkins.jobs.create(WorkflowJob.class);
        jobA.script.set("node('master') {\n sleep 10\n}");
        jobA.save();

        elasticSleep(1000);
        jobA.configure();
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("Hello World");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed();
    }

    public void _testSimpleCIEventTriggerWithCheckOnPipelineJob() {
        WorkflowJob jobA = jenkins.jobs.create(WorkflowJob.class);
        jobA.script.set("node('master') {\n sleep 10\n}");
        jobA.save();

        elasticSleep(1000);
        jobA.configure();
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        MsgCheck check = pd.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        jobA.save();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed();
    }

    public void _testSimpleCIEventTriggerOnPipelineJobWithGlobalEnvVarInTopic() {

        String script = "import hudson.slaves.EnvironmentVariablesNodeProperty\n" +
                "import jenkins.model.Jenkins\n" +
                "\n" +
                "instance = Jenkins.getInstance()\n" +
                "globalNodeProperties = instance.getGlobalNodeProperties()\n" +
                "envVarsNodePropertyList = globalNodeProperties.getAll(EnvironmentVariablesNodeProperty.class)\n" +
                "\n" +
                "newEnvVarsNodeProperty = null\n" +
                "envVars = null\n" +
                "\n" +
                "if ( envVarsNodePropertyList == null || envVarsNodePropertyList.size() == 0 ) {\n" +
                "  newEnvVarsNodeProperty = new EnvironmentVariablesNodeProperty();\n" +
                "  globalNodeProperties.add(newEnvVarsNodeProperty)\n" +
                "  envVars = newEnvVarsNodeProperty.getEnvVars()\n" +
                "} else {\n" +
                "  envVars = envVarsNodePropertyList.get(0).getEnvVars()\n" +
                "}\n" +
                "\n" +
                "envVars.put(\"MY_TOPIC_ID\", \"MY_UUID\")\n" +
                "\n" +
                "instance.save()";
        jenkins.runScript(script);

        WorkflowJob jobA = jenkins.jobs.create(WorkflowJob.class);
        jobA.script.set("node('master') {\n sleep 10\n}");
        jobA.save();

        elasticSleep(1000);
        jobA.configure();
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.overrides.check();
        pd.topic.set("$MY_TOPIC_ID");
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("$MY_TOPIC_ID");
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("Hello World");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed();
    }

    public void _testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic() {

        String script = "import hudson.slaves.EnvironmentVariablesNodeProperty\n" +
                "import jenkins.model.Jenkins\n" +
                "\n" +
                "instance = Jenkins.getInstance()\n" +
                "globalNodeProperties = instance.getGlobalNodeProperties()\n" +
                "envVarsNodePropertyList = globalNodeProperties.getAll(EnvironmentVariablesNodeProperty.class)\n" +
                "\n" +
                "newEnvVarsNodeProperty = null\n" +
                "envVars = null\n" +
                "\n" +
                "if ( envVarsNodePropertyList == null || envVarsNodePropertyList.size() == 0 ) {\n" +
                "  newEnvVarsNodeProperty = new EnvironmentVariablesNodeProperty();\n" +
                "  globalNodeProperties.add(newEnvVarsNodeProperty)\n" +
                "  envVars = newEnvVarsNodeProperty.getEnvVars()\n" +
                "} else {\n" +
                "  envVars = envVarsNodePropertyList.get(0).getEnvVars()\n" +
                "}\n" +
                "\n" +
                "envVars.put(\"MY_TOPIC_ID\", \"MY_UUID\")\n" +
                "\n" +
                "instance.save()";
        jenkins.runScript(script);

        WorkflowJob jobA = jenkins.jobs.create(WorkflowJob.class);
        jobA.script.set("node('master') {\n sleep 10\n}");
        jobA.save();

        elasticSleep(1000);
        jobA.configure();
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.overrides.check();
        pd.topic.set("$MY_TOPIC_ID");
        MsgCheck check = pd.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        jobA.save();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("$MY_TOPIC_ID");
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed();
    }

    public void _testSimpleCIEventTriggerWithPipelineWaitForMsg() {
        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n def scott = waitForCIMessage  providerName: 'test', " +
                " selector: " +
                " \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\"  \necho \"scott = \" + scott}");
        wait.save();
        wait.startBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("Hello World");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        wait.getLastBuild().shouldSucceed();
        assertThat(wait.getLastBuild().getConsole(), containsString("Hello World"));
    }

    public void _testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg() {
        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n def scott = waitForCIMessage  providerName: 'test', " +
                " checks: [[field: '" + MESSAGE_CHECK_FIELD + "', expectedValue: '" + MESSAGE_CHECK_VALUE + "']]\n" +
                "}");
        wait.save();
        wait.startBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        wait.getLastBuild().shouldSucceed();
        assertThat(wait.getLastBuild().getConsole(), containsString("catch me"));
    }

    public void _testSimpleCIEventTriggerWithSelectorWithCheckWithPipelineWaitForMsg() {
        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n def scott = waitForCIMessage  providerName: 'test'," +
                " selector: \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\"," +
                " checks: [[field: '" + MESSAGE_CHECK_FIELD + "', expectedValue: '" + MESSAGE_CHECK_VALUE + "']]\n" +
                "}");
        wait.save();
        wait.startBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        wait.getLastBuild().shouldSucceed();
        assertThat(wait.getLastBuild().getConsole(), containsString("catch me"));

        FreeStyleJob jobC = jenkins.jobs.create();
        jobC.configure();
        notifier = jobC.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("{\"content\": \"uncaught\"}");
        jobC.save();
        jobC.startBuild().shouldSucceed();

        elasticSleep(3000);
        assertThat(wait.getLastBuild().getNumber(), is(equalTo(1)));
    }

    public void _testSimpleCIEventSendAndWaitPipeline(WorkflowJob send, String expected) {
        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n def scott = waitForCIMessage providerName: 'test'," +
                "selector: " +
                " \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\",  " +
                " topic: 'org.fedoraproject.otopic'" +
                "\necho \"scott = \" + scott}");
        wait.save();
        wait.startBuild();

        send.configure();
        send.script.set("node('master') {\n sendCIMessage" +
                " providerName: 'test', " +
                " topic: 'org.fedoraproject.otopic'," +
                " messageContent: 'abcdefg', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'}");
        send.save();
        send.startBuild().shouldSucceed();

        elasticSleep(1000);
        wait.getLastBuild().shouldSucceed();
        assertThat(wait.getLastBuild().getConsole(), containsString(expected));
    }

    public void _testSimpleCIEventSendAndWaitPipelineWithVariableTopic(WorkflowJob send, String selector,
                                                                       String expected) {
        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n" +
                "    env.MY_TOPIC = 'org.fedoraproject.my-topic'\n" +
                "    def scott = waitForCIMessage providerName: \"test\", selector:  \"" +
                selector + "${env.MY_TOPIC}'\",        overrides: [topic: \"${env.MY_TOPIC}\"]\n" +
                "    echo \"scott = \" + scott\n" +
                "}");
        wait.save();
        wait.startBuild();

        send.configure();
        send.script.set("node('master') {\n" +
                " env.MY_TOPIC = 'org.fedoraproject.my-topic'\n" +
                " sendCIMessage providerName: \"test\", overrides: [topic: \"${env.MY_TOPIC}\"], messageContent: 'abcdefg', messageProperties: 'CI_STATUS = failed', messageType: 'CodeQualityChecksDone'\n" +
                "}");
        send.save();
        send.startBuild().shouldSucceed();

        elasticSleep(1000);
        wait.getLastBuild().shouldSucceed();
        assertThat(wait.getLastBuild().getConsole(),
                containsString(expected));
    }

    public boolean isSubscribed(String job) {
        try {
            JenkinsLogger logger = jenkins.getLogger("all");
            logger.waitForLogged(Pattern.compile("Successfully subscribed job \'" +
                    job + "\' to.*"));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public void _testJobRename() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();
        elasticSleep(1000);

        jobA.renameTo("ABC");
        elasticSleep(3000);
        assertThat("Trigger not subscribed", isSubscribed("ABC"));
    }

    public void _testJobRenameWithCheck() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        MsgCheck check = pd.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        jobA.save();
        elasticSleep(1000);

        jobA.renameTo("ABC");
        elasticSleep(3000);
        assertThat("Trigger not subscribed", isSubscribed("ABC"));
    }

    public void _testDisabledJobDoesNotGetTriggered() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.disable();
        jobA.save();
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(5000);
        jobA.getLastBuild().shouldNotExist();

        jobA.configure();
        jobA.check((find(by.checkbox("disable"))), false);
        jobA.save();
        elasticSleep(5000);
        jobB.startBuild().shouldSucceed();
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
    }

    public void _testDisabledJobDoesNotGetTriggeredWithCheck() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo job ran");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        MsgCheck check = pd.addMsgCheck();
        check.field.set(MESSAGE_CHECK_FIELD);
        check.expectedValue.set(MESSAGE_CHECK_VALUE);
        jobA.disable();
        jobA.save();
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageContent.set(MESSAGE_CHECK_CONTENT);
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(5000);
        jobA.getLastBuild().shouldNotExist();

        jobA.configure();
        jobA.check((find(by.checkbox("disable"))), false);
        jobA.save();
        elasticSleep(5000);
        jobB.startBuild().shouldSucceed();
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo job ran"));
    }

    public void _testEnsureFailedSendingOfMessageFailsBuild() {
        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierBuildStep notifier = jobB.addBuildStep(CINotifierBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.failOnError.check();
        jobB.save();
        jobB.startBuild().waitUntilFinished().shouldFail();
        assertThat(jobB.getLastBuild().getConsole(), containsString("Unhandled exception in perform: "));
    }

    public void _testEnsureFailedSendingOfMessageFailsPipelineBuild() {
        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        send.configure();
        send.script.set("node('master') {\n sendCIMessage" +
                " providerName: 'test', " +
                " failOnError: true, " +
                " messageContent: 'abcdefg', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'}");
        send.save();
        send.startBuild().waitUntilFinished().shouldFail();
        assertThat(send.getLastBuild().getConsole(), containsString("Unhandled exception in perform: "));
    }

    public void _testAbortWaitingForMessageWithPipelineBuild() throws IOException {
        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n def scott = waitForCIMessage  providerName: 'test', " +
                " selector: " +
                " \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\"  \n}");
        wait.save();
        Build waitingBuild = wait.startBuild();
        elasticSleep(3000);

        HttpClient httpclient = new DefaultHttpClient();
        HttpPost post = new HttpPost(waitingBuild.url("stop").toExternalForm());
        HttpResponse response = httpclient.execute(post);
        if (response.getStatusLine().getStatusCode() >= 400) {
            throw new IOException("Failed to stop build: " + response.getStatusLine() + "\n" +
                    IOUtils.toString(response.getEntity().getContent()));
        } else {
            System.out.println("Build stopped! (status code: " + response.getStatusLine().getStatusCode() + ")");
        }

        waitingBuild.shouldAbort();
    }

    public void _testPipelineInvalidProvider() throws Exception {
        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        send.script.set("node('master') {\n def message = sendCIMessage " +
                " providerName: 'bogus', " +
                " messageContent: '', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'}\n");
        send.save();
        send.startBuild().shouldFail();
        assertThat(send.getLastBuild().getConsole(), containsString("java.lang.Exception: Unrecognized provider name."));

        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n def scott = waitForCIMessage  providerName: 'bogus', " +
                " selector: " +
                " \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\"  \necho \"scott = \" + scott}");
        wait.save();
        wait.startBuild().shouldFail();
        assertThat(wait.getLastBuild().getConsole(), containsString("java.lang.Exception: Unrecognized provider name."));
    }

    protected String stringFrom(Process proc) throws InterruptedException, IOException {
        assertThat(proc.waitFor(), is(equalTo(0)));
        StringWriter writer = new StringWriter();
        IOUtils.copy(proc.getInputStream(), writer);
        String string = writer.toString();
        writer.close();
        return string;
    }

    protected Process logProcessBuilderIssues(ProcessBuilder pb, String commandName) throws InterruptedException, IOException {
        String dir = "";
        if (pb.directory() != null) {
            dir = pb.directory().getAbsolutePath();
        }
        System.out.println("Running : " + pb.command() + " => directory: " + dir);
        Process processToRun = pb.start();
        int result = processToRun.waitFor();
        if (result != 0) {
            StringWriter writer = new StringWriter();
            IOUtils.copy(processToRun.getErrorStream(), writer);
            System.out.println("Issue occurred during command \"" + commandName + "\":\n" + writer.toString());
            writer.close();
        }
        return processToRun;
    }

    protected void stopContainer(DockerContainer container) throws Exception {
        System.out.println(Docker.cmd("stop", container.getCid())
                .popen()
                .verifyOrDieWith("Unable to stop container"));
        elasticSleep(3000);
        boolean running = false;
        try {
            container.assertRunning();
            running = false;
        }
        catch (Error e) {
            //This is ok
        }
        if (running) {
            throw new Exception("Container " + container.getCid() + " not stopped");
        }
    }

    protected void printThreadsWithName(String tName) {
        System.out.println("Looking for Threads with name that contains: " + tName);
        String script = "import java.util.*\n" +
                "import java.util.regex.*\n" +
                "import com.github.olivergondza.dumpling.model.ThreadSet;\n" +
                "import static com.github.olivergondza.dumpling.model.ProcessThread.nameContains;\n" +
                "ThreadSet ts =  D.runtime.threads.where(nameContains(Pattern.compile(\"" + tName + "\")))\n" +
                "println(\"Filtered Thread Size: \" + ts.size());\n" +
                "Iterator it = ts.iterator();\n" +
                "while (it.hasNext()) {\n" +
                "  println(it.next().name)\n" +
                "}";
        String threads = jenkins.runScript(script);
        System.out.println(threads);
    }

    protected int getCurrentThreadCountForName(String name) {
        String threadCount =
                jenkins.runScript("println D.runtime.threads.grep { it.name =~ /^" + name + "/ }.size()");
        return Integer.parseInt(threadCount.trim());
    }

}

