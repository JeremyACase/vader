The user's request: <userRequest>

The plan for that request, in the order its tasks run (objective: <planObjective>):
<plan>

Task under review: <taskTitle>

Description: <taskDescription>

<if(succeeded)>The agent reported SUCCESS with this result:<else>The agent reported FAILURE with this reason:<endif>
<if(succeeded)><result><else><failureReason><endif>

Evidence from the agent's tool-call log (recorded by the system, not by the agent):
<if(toolEvidence)><toolEvidence><else>The agent made no tool calls.<endif>

Prior updates already recorded against this task:
<if(priorUpdates)><priorUpdates><else>(none)<endif>
