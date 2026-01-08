Feature: NotificationAggregate

    Scenario: Receive new email notification without material Url

      Given no previous events
      When you send to a Notification using an email
      Then notification queued

    Scenario: Receive new email notification with material Url

      Given no previous events
      When you sendWithMaterialAttachment to a Notification using an emailWithMaterialUrl
      Then notification queued with material url

    Scenario: Receive new email notification with file id

      Given no previous events
      When you sendWithFileIdAttachment to a Notification using an emailWithFileId
      Then notification queued with file id

    Scenario: Mark a notification as sent successfully

      Given notification queued
      When you markAsSent to a Notification with a sent time
      Then notification sent

    Scenario: Mark a notification as permanently failed

      Given notification queued
      When you markAsFailed to a Notification with a failure details
      Then notification failed

    Scenario: Mark a notification as attempted

      Given notification queued
      When you markAsAttempted to a Notification with an attempt details
      Then notification attempted

    Scenario: Receive new letter notification

      Given no previous events
      When you send to a Notification using a letter
      Then letter queued

    Scenario: Receive new first class letter notification

      Given no previous events
      When you send to a Notification using a first class letter
      Then first class letter queued

    Scenario: Mark a letter notification as sent successfully

      Given letter queued
      When you markAsSent to a Notification with a sent time
      Then notification sent

    Scenario: Mark a letter notification as url link failed

      Given letter queued
      When you markAsFailed to a Notification with a document download failure details
      Then notification failed

    Scenario: Mark the letter for resend if check status returns validation failed

      Given letter queued
      When you markAsInvalid to a Notification with a validation failed response for check status
      Then letter queued for resend

    Scenario: Mark the first class letter for resend if check status returns validation failed

      Given first class letter queued
      When you markAsInvalid to a Notification with a validation failed response for check status
      Then first class letter queued for resend

    Scenario: Mark a notification as permanently failed when resend fails after 5 attempts
      Given letter queued
      Given letter queued for resend
      Given letter queued for resend
      Given letter queued for resend
      Given letter queued for resend
      Given letter queued for resend
      When  you markAsInvalid to a Notification with a validation failed response for check status
      Then notification failed for validation status failure

    Scenario: Stop resending the letter after check status returns a accept status
       Given letter queued
       Given letter queued for resend
       Given letter queued for resend
       Given letter queued for resend
       When you markAsSent to a Notification with a sent time
       Then notification sent