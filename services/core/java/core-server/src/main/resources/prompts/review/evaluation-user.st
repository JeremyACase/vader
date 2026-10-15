The user's request: <userRequest>

The plan for that request, in the order its tasks run (objective: <planObjective>):
<plan>

Task under review: <taskTitle>

Description: <taskDescription>

<if(succeeded)>The agent reported SUCCESS with this result:<else>The agent reported FAILURE with this reason:<endif>
<if(succeeded)><result><else><failureReason><endif>

Evidence from the agent's tool-call log (recorded by the system, not by the agent):
<if(toolEvidence)><toolEvidence><else>The agent made no tool calls.<endif>

Files the attempt saved for the user (recorded by the system, not by the agent):
<if(uploadedFiles)><uploadedFiles><else>The attempt saved no files.<endif>
