package com.example.blockminer.feature.impl.AutoGetStats.tasks;

public abstract class AbstractInventoryTask<T> {
	protected TaskStatus taskStatus = TaskStatus.PENDING;
	protected String error;

	public abstract void init();

	public abstract void onTick();

	public abstract void end();

	public abstract T getResult();

	public TaskStatus getTaskStatus() {
		return taskStatus;
	}

	public String getError() {
		return error;
	}

	protected void fail(String message) {
		error = message;
		taskStatus = TaskStatus.FAILURE;
	}
}
