package org.example.camunda.process.solution.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.tasklist.dto.TaskState;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeCustomHeaders;
import io.camunda.zeebe.spring.client.annotation.ZeebeVariablesAsType;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.example.camunda.process.solution.facade.dto.Task;
import org.example.camunda.process.solution.utils.BpmnUtils;
import org.example.camunda.process.solution.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserTaskWorker {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskWorker.class);

  @Autowired private SimpMessagingTemplate simpMessagingTemplate;

  @ZeebeWorker(type = "io.camunda.zeebe:userTask", timeout = 2592000000L) // set timeout to 30 days
  public void listenUserTask(
      final JobClient client,
      final ActivatedJob job,
      @ZeebeVariablesAsType Map<String, Object> variables,
      @ZeebeCustomHeaders Map<String, String> headers) {

    try {

      LOG.info("User Task Worker triggered with variables: " + variables);

      Task task = new Task();

      task.setVariables(variables);

      String processDefinitionKey = Long.toString(job.getProcessDefinitionKey());
      task.setProcessDefinitionId(processDefinitionKey);

      String formKey = headers.get("io.camunda.zeebe:formKey");
      task.setFormKey(formKey);

      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
      String creationTime = sdf.format(new Date());
      task.setCreationTime(creationTime);

      // Note: Job Key != Task Id :-(
      String jobKey = Long.toString(job.getKey());

      // But good news, job.getElementInstanceKey() is the "taskId" that is expected by task list
      // graphql
      String taskId = Long.toString(job.getElementInstanceKey());
      task.setId(taskId);

      // BUT!!! It's possible to send request to task list graphql to complete a request before the
      // graphql knows about the task (because the exporter still hasn't finished). So, it's better
      // to
      // use zeebe client to complete the tasks.
      // And if we're using zeebe client, we need to use job key
      task.setJobKey(jobKey);

      String bpmnProcessId = job.getBpmnProcessId();
      task.setProcessName(BpmnUtils.getProcessName(bpmnProcessId + ".bpmn", bpmnProcessId));

      String taskActivityId = job.getElementId();
      // !!! The name of the bpmn file in the "src/main/resources/models" directory must match the
      // process id in order for this to work!
      String taskName = BpmnUtils.getTaskNameFromBpmn(bpmnProcessId + ".bpmn", taskActivityId);
      task.setName(taskName);

      if (!job.getCustomHeaders().isEmpty()) {
        if (job.getCustomHeaders().containsKey("io.camunda.zeebe:assignee")) {
          task.setAssignee(job.getCustomHeaders().get("io.camunda.zeebe:assignee"));
        }
        if (job.getCustomHeaders().containsKey("io.camunda.zeebe:candidateGroups")) {
          String groups = job.getCustomHeaders().get("io.camunda.zeebe:candidateGroups");
          task.setCandidateGroups(
              JsonUtils.toParametrizedObject(groups, new TypeReference<List<String>>() {}));
        }
      }

      TaskState taskState = TaskState.CREATED;
      task.setTaskState(taskState);
      if (task.getAssignee() != null) {
        simpMessagingTemplate.convertAndSend("/topic/" + task.getAssignee() + "/userTask", task);
      } else {
        simpMessagingTemplate.convertAndSend("/topic/userTask", task);
      }
    } catch (Exception e) {
      client.newFailCommand(job.getKey()).retries(0).send();
    }
  }
}