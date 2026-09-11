package org.vader.core.server.service.agent;

import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.models.ResultRequest;
import org.vader.core.server.repository.TaskAttemptRepository;

/**
 * Settles a {@code TaskAttempt} that a harness is never going to report back on: dispatched but
 * never started (crash-loop, image pull failure, scheduling failure), or started and gone silent
 * (crash, OOM kill, network partition) -- with no notion of "still alive but slow," a harness that
 * never calls back would otherwise leave its attempt open, and the owning workflow waiting,
 * forever.
 *
 * <p>An open attempt's most recent sign of life is its last heartbeat, or failing that when it
 * was dispatched, or failing that when it was created. Anything older than
 * {@code deadline-seconds + reaper.grace-period-seconds} -- the harness's own deadline, plus
 * slack for scheduling/image-pull time and the final network round trip -- is reaped: settled as
 * {@code TIMED_OUT} through the normal {@link TaskAttemptService#submitResult} path, so the
 * scheduler retries or fails the task exactly as it would for a harness-reported timeout.</p>
 */
@Service
public class TaskAttemptReaper {

    private static final Logger logger = LoggerFactory.getLogger(TaskAttemptReaper.class);

    private static final List<TaskAttemptStatus> OPEN_STATUSES = List.of(
        TaskAttemptStatus.PENDING, TaskAttemptStatus.DISPATCHED, TaskAttemptStatus.RUNNING);

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskAttemptService taskAttemptService;

    @Value("${vader.agent-harness.deadline-seconds:600}")
    private long deadlineSeconds;

    @Value("${vader.agent-harness.reaper.grace-period-seconds:300}")
    private long gracePeriodSeconds;

    /**
     * Reaps every attempt that has gone silent for longer than the allowed window.
     */
    @Scheduled(fixedDelayString = "${vader.agent-harness.reaper.poll-interval-ms:30000}")
    public void reap() {
        var staleBefore = OffsetDateTime.now().minusSeconds(this.staleAfterSeconds());
        var stale = this.taskAttemptRepository.findStale(OPEN_STATUSES, staleBefore);
        stale.forEach(this::reapOne);
    }

    private void reapOne(final TaskAttemptEntity attempt) {
        logger.warn("Reaping stuck task attempt {} (status={})",
            attempt.getId(), attempt.getStatus());
        var reason = "Reaped: no contact from the harness within " + this.staleAfterSeconds()
            + "s of dispatch/last heartbeat.";
        try {
            this.taskAttemptService.submitResult(
                attempt.getId(), new ResultRequest(TaskAttemptStatus.TIMED_OUT, null, reason));
        } catch (RuntimeException e) {
            // One attempt settling underneath the reaper (e.g. the harness reported in right as
            // this ran) must not stop the rest of the batch from being reaped.
            logger.warn("Could not reap task attempt {}: {}", attempt.getId(), e.getMessage());
        }
    }

    private long staleAfterSeconds() {
        return this.deadlineSeconds + this.gracePeriodSeconds;
    }
}
