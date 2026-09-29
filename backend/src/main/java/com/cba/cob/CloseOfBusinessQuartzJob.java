package com.cba.cob;

import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Fired nightly by the single CoB Quartz trigger; runs every CoB job in sequence
 * for today's business date via {@link CobRunner}.
 *
 * <p>{@code @DisallowConcurrentExecution}: if a run is still going when the trigger
 * fires again (e.g. a misfire catch-up right after start-up), Quartz waits rather
 * than starting a second, overlapping Close of Business.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class CloseOfBusinessQuartzJob extends QuartzJobBean {

    private final CobRunner cobRunner;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        cobRunner.runAll(LocalDate.now());
    }
}
