package com.lts.job.queue.mysql;

import com.lts.job.core.cluster.Config;
import com.lts.job.core.file.FileUtils;
import com.lts.job.core.util.DateUtils;
import com.lts.job.core.util.JobQueueUtils;
import com.lts.job.queue.ExecutableJobQueue;
import com.lts.job.queue.domain.JobPo;
import com.lts.job.queue.exception.JobQueueException;
import com.lts.job.queue.mysql.support.ResultSetHandlerHolder;
import com.lts.job.store.jdbc.SqlExecutor;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Robert HG (254963746@qq.com) on 5/31/15.
 */
public class MysqlExecutableJobQueue extends AbstractMysqlJobQueue implements ExecutableJobQueue {

    public MysqlExecutableJobQueue(Config config) {
        super(config);
    }

    @Override
    public boolean createQueue(String taskTrackerNodeGroup) {
        // create table
        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream("sql/lts_executable_job_queue.sql");
            String sql = FileUtils.read(is);
            getSqlTemplate().update(getRealSql(sql, taskTrackerNodeGroup));
            return true;
        } catch (Exception e) {
            throw new JobQueueException("create table error!", e);
        }
    }

    private String getTableName(String taskTrackerNodeGroup) {
        return JobQueueUtils.getExecutableQueueName(taskTrackerNodeGroup);
    }

    private String getRealSql(String sql, String taskTrackerNodeGroup) {
        return sql.replace("{tableName}", getTableName(taskTrackerNodeGroup));
    }

    @Override
    public boolean add(JobPo jobPo) {
        jobPo.setGmtCreated(DateUtils.currentTimeMillis());
        jobPo.setGmtModified(jobPo.getGmtCreated());
        return super.add(getTableName(jobPo.getTaskTrackerNodeGroup()), jobPo);
    }

    @Override
    public JobPo take(final String taskTrackerNodeGroup, final String taskTrackerIdentity) {
        return getSqlTemplate().executeInTransaction(new SqlExecutor<JobPo>() {
            @Override
            public JobPo run(Connection conn) throws SQLException {
                Long now = System.currentTimeMillis();
                // select for update
                String selectForUpdateSql = "SELECT *" +
                        " FROM `{tableName}` " +
                        " WHERE `task_tracker_node_group` = ? " +
                        " AND is_running = ? " +
                        " AND `trigger_time` < ? " +
                        " ORDER BY `trigger_time` ASC, `priority` ASC, `gmt_created` ASC " +
                        " LIMIT 0, 1 FOR UPDATE";
                Object[] selectParams = new Object[]{taskTrackerNodeGroup, false, now};
                JobPo jobPo = getSqlTemplate().query(conn, getRealSql(selectForUpdateSql, taskTrackerNodeGroup),
                        ResultSetHandlerHolder.JOB_PO_RESULT_SET_HANDLER, selectParams
                );
                if (jobPo != null) {
                    String updateSql = "UPDATE `{tableName}` SET " +
                            "`is_running` = ?, " +
                            "`task_tracker_identity` = ?, " +
                            "`gmt_modified` = ?," +
                            "`prev_exe_time` = ? " +
                            " WHERE job_id = ?";
                    Object[] params = new Object[]{
                            true, taskTrackerIdentity, now, now, jobPo.getJobId()
                    };
                    getSqlTemplate().update(conn, getRealSql(updateSql, taskTrackerNodeGroup), params);

                    jobPo.setIsRunning(true);
                    jobPo.setTaskTrackerIdentity(taskTrackerIdentity);
                    jobPo.setGmtModified(now);
                    jobPo.setPrevExeTime(now);
                }
                return jobPo;
            }
        });
    }

    @Override
    public boolean remove(String taskTrackerNodeGroup, String jobId) {
        String deleteSql = "DELETE FROM `{tableName}` WHERE job_id = ?";
        try {
            return getSqlTemplate().update(getRealSql(deleteSql, taskTrackerNodeGroup), jobId) == 1;
        } catch (SQLException e) {
            throw new JobQueueException(e);
        }
    }

    @Override
    public void resume(JobPo jobPo) {
        String updateSql = "UPDATE `{tableName}` SET " +
                "`is_running` = ?," +
                "`task_tracker_identity` = ?," +
                "`gmt_modified` = ?" +
                " WHERE job_id = ? ";
        try {
            Object[] params = new Object[]{false, null, System.currentTimeMillis(), jobPo.getJobId()};
            getSqlTemplate().update(getRealSql(updateSql, jobPo.getTaskTrackerNodeGroup()), params);
        } catch (SQLException e) {
            throw new JobQueueException(e);
        }
    }
}