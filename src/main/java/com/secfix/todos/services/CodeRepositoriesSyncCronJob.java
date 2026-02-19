package com.secfix.todos.services;
import com.secfix.todos.database.models.CodeRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CodeRepositoriesSyncCronJob {
    Logger logger = LoggerFactory.getLogger(CodeRepositoriesSyncCronJob.class);

    @Autowired
    private CodeRepositoriesManagementService codeRepositoriesManagementService;

    @Autowired
    private GithubServiceWrapper githubServiceWrapper;

    @Scheduled(cron = "0 0/60 * * * ?") // every hour
    public void syncGithubrepositories() {
        logger.info("Sync Github repositories started");
        try {
            List<CodeRepository> activeRepos = this.codeRepositoriesManagementService.getActiveCodeRepositories();

            for (CodeRepository codeRepository : activeRepos) {
                codeRepository = this.githubServiceWrapper.getRepository(codeRepository);
            }

            this.codeRepositoriesManagementService.batchUpdateCodeRepositories(activeRepos);

            logger.info("Sync Github repositories completed");
        } catch (Exception ex) {
            logger.info("Sync Github repositories failed", ex);
        }

    }
}
