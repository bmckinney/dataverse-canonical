package edu.harvard.iq.dataverse.batch.jobs.importer.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.UserNotification;
import edu.harvard.iq.dataverse.UserNotificationServiceBean;
import edu.harvard.iq.dataverse.UserServiceBean;
import edu.harvard.iq.dataverse.actionlogging.ActionLogServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.batch.entities.JobExecutionEntity;
import edu.harvard.iq.dataverse.batch.util.LoggingUtil;

import javax.batch.api.BatchProperty;
import javax.batch.api.listener.JobListener;
import javax.batch.api.listener.StepListener;
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.JobExecution;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.ejb.EJB;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

@Named
@Dependent
public class FileRecordJobListener implements StepListener, JobListener {

    private static final Logger logger = Logger.getLogger(FileRecordJobListener.class.getName());

    private static final UserNotification.Type notifyType = UserNotification.Type.FILESYSTEMIMPORT;

    @Inject
    private JobContext jobContext = null;

    @Inject
    private StepContext stepContext;

    @Inject
    @BatchProperty
    private String logDir;

    @EJB
    UserNotificationServiceBean notificationServiceBean;

    @EJB
    AuthenticationServiceBean authenticationServiceBean;

    @EJB
    ActionLogServiceBean actionLogServiceBean;

    @EJB
    DatasetServiceBean datasetServiceBean;

    @Override
    public void afterStep() throws Exception {
        // no-op
    }

    @Override
    public void beforeStep() throws Exception {
        // no-op
    }

    @Override
    public void beforeJob() throws Exception {
        // no-op
    }

    @Override
    public void afterJob() throws Exception {
        doReport();
        logger.log(Level.INFO, "After Job {0}, instance {1} and execution {2}, batch status [{3}], exit status [{4}]",
                new Object[]{jobContext.getJobName(), jobContext.getInstanceId(), jobContext.getExecutionId(),
                        jobContext.getBatchStatus(), jobContext.getExitStatus()});
    }

    private void doReport() {

        try {

            Dataset dataset = null;
            AuthenticatedUser user = null;
            String jobJson;
            String jobId = Long.toString(jobContext.getInstanceId());

            JobOperator jobOperator = BatchRuntime.getJobOperator();
            Properties jobParams = jobOperator.getParameters(jobContext.getInstanceId());

            // determine user and dataset IDs based on job params
            if (jobParams.containsKey("datasetId")) {
                String datasetId = jobParams.getProperty("datasetId");
                dataset = datasetServiceBean.findByGlobalId(datasetId);
            }

            if (jobParams.containsKey("datasetPrimaryKey")) {
                long datasetPrimaryKey = Long.parseLong(jobParams.getProperty("datasetPrimaryKey"));
                dataset = datasetServiceBean.find(datasetPrimaryKey);
            }

            if (jobParams.containsKey("userPrimaryKey")) {
                long userPrimaryKey = Long.parseLong(jobParams.getProperty("userPrimaryKey"));
                user = authenticationServiceBean.findByID(userPrimaryKey);
            }
            if (jobParams.containsKey("userId")) {
                String userId = jobParams.getProperty("userId");
                user = authenticationServiceBean.getAuthenticatedUser(userId);
            }

            if (user == null) {
                logger.log(Level.SEVERE, "Cannot find authenticated user.");
                return;
            }

            if (dataset == null) {
                logger.log(Level.SEVERE, "Cannot find dataset.");
                return;
            }
            long datasetVersionId = dataset.getLatestVersion().getId();

            logger.log(Level.INFO, "FileRecordJobListener Dataset: " + dataset.getGlobalId());

            JobExecution jobExecution = jobOperator.getJobExecution(jobContext.getInstanceId());
            if (jobExecution != null) {

                Date date = new Date();
                Timestamp timestamp =  new Timestamp(date.getTime());

                JobExecutionEntity jobExecutionEntity = JobExecutionEntity.create(jobExecution);
                jobExecutionEntity.setExitStatus("COMPLETED");
                jobExecutionEntity.setStatus(BatchStatus.COMPLETED);
                jobExecutionEntity.setEndTime(date);
                jobJson = new ObjectMapper().writeValueAsString(jobExecutionEntity);

                // [1] save json log to file
                LoggingUtil.saveJsonLog(jobJson, logDir, jobId);
                // [2] send user notifications
                notificationServiceBean.sendNotification(user, timestamp, notifyType, datasetVersionId);
                // [3] action log it
                actionLogServiceBean.log(LoggingUtil.getActionLogRecord(user.getIdentifier(), jobExecution, jobJson, jobId));

            } else {
                logger.log(Level.SEVERE, "Job execution is null");
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating job json: " + e.getMessage());
        }
    }

}
