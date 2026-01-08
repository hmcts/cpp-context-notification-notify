package uk.gov.moj.notification.notify.it.util;

public class FrameworkConstants {

    public static final String CONTEXT_NAME = "notificationnotify";
    public static final String JMP_TOPIC = "jms.topic.";
    public static final String PUBLIC_TOPIC_NAME = JMP_TOPIC + "public.event";
    public static final String PUBLIC_EVENT_SENT = "public.notificationnotify.events.notification-sent";
    public static final String PUBLIC_EVENT_FAILED = "public.notificationnotify.events.notification-failed";
}
