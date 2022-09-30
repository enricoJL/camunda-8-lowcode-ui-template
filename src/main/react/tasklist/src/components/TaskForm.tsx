import React, { useState, useEffect } from 'react';
import {useDispatch, useSelector} from 'react-redux';
import type {} from 'redux-thunk/extend-redux';
import authService from '../service/AuthService';
import taskService from '../service/TaskService';
import FormViewer from './FormViewer';


function TaskForm(props: any) {
  const dispatch = useDispatch();
  const currentTask = useSelector((state: any) => state.process.currentTask)
  const currentSchema = useSelector((state: any) => state.process.currentFormSchema)
  const disabled = currentTask && currentTask.assignee && currentTask.assignee != authService.getUser()!.username;

  const claim = () => {
	dispatch(taskService.claim());
  }
  const unclaim = () => {
	dispatch(taskService.unclaim());
  }
  return (
	currentTask ?
	  <div className="card taskform">
        <h5 className="card-title" > {currentTask.name} {currentTask.assignee ? <span className="assignedTo"> (assigned to {currentTask.assignee})</span> : <span></span>}</h5>
        {currentTask.assignee ?
          <button disabled={disabled} type="button" className="btn btn-dark btnClaimUnClaim" onClick={unclaim}> Unclaim</button>
        :
          <button type="button" className="btn btn-dark btnClaimUnClaim" onClick={claim}>Claim</button>
		}
		<FormViewer schema={currentSchema} variables={currentTask.variables} disabled={disabled}></FormViewer>
	  </div> : <div />
  )
  
}

export default TaskForm;

