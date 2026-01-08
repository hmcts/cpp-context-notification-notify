package uk.gov.moj.cpp.notification.notify.event.processor.scheduler;

import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.POCA_EMAIL;
import static uk.gov.moj.cpp.notification.notify.event.processor.util.EmailSchedulerConstants.DOUBLE_QUOTES;
import static uk.gov.moj.cpp.notification.notify.event.processor.util.EmailSchedulerConstants.MAIL_SERVER_CREDENTIALS;
import static uk.gov.moj.cpp.notification.notify.event.processor.util.EmailSchedulerConstants.SINGLE_QUOTE;
import static uk.gov.moj.cpp.notification.notify.event.processor.util.EmailSchedulerConstants.UNICODE_CHARACTER;

import uk.gov.justice.services.common.configuration.Value;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Startup
@ApplicationScoped
public class PocaEmailsScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PocaEmailsScheduler.class);
    private static final String TIMER_TIMEOUT_INFO = "PocaEmailsScheduler timer triggered.";

    @Inject
    @Value(key = "poca.email.scheduler.interval", defaultValue = "120000")
    private String schedulerInterval;

    @Resource
    private TimerService timerService;

    @Inject
    @Value(key = "poca_mail_server_settings")
    private String mailServerSettingsJson;

    @Inject
    private UtcClock utcClock;
    @Inject
    private StringToJsonObjectConverter stringToJsonObjectConverter;
    @Inject
    private ExecutionService executionService;

    private JsonArray mailServerSettings;

    @PostConstruct
    public void init() {
        setMailServerSettings();
        timerService.createIntervalTimer(
                30000L,
                Long.parseLong(this.schedulerInterval),
                new TimerConfig(TIMER_TIMEOUT_INFO, false));
    }

    @Timeout
    public void startTimer() {
        LOGGER.info("PocaEmailsScheduler Triggers");
        mailServerSettings.forEach(mailServerSetting -> {
            final ExecutionInfo executionInfo = new ExecutionInfo((JsonObject) mailServerSetting, POCA_EMAIL.getTaskName(), utcClock.now(), STARTED, Priority.MEDIUM);
            executionService.executeWith(executionInfo);
        });
    }

    public JsonArray getMailServerSettings() {
        return stringToJsonObjectConverter.convert(mailServerSettingsJson).getJsonArray(MAIL_SERVER_CREDENTIALS);
    }

    @PreDestroy
    public void cleanup() {
        timerService.getTimers().forEach(Timer::cancel);
    }


    private void setMailServerSettings() {
        // replace unicode character
        mailServerSettingsJson = mailServerSettingsJson.replace(UNICODE_CHARACTER, DOUBLE_QUOTES).replace(SINGLE_QUOTE, DOUBLE_QUOTES);
        mailServerSettings = stringToJsonObjectConverter.convert(mailServerSettingsJson).getJsonArray(MAIL_SERVER_CREDENTIALS);
    }
}