package com.example.blockminer.macro.impl.GlacialMacro.states;

import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;

public final class ErrorHandlingState implements GlacialMacroState {
	private final String reason;

	public ErrorHandlingState(String reason) {
		this.reason = reason;
	}

	@Override
	public void onStart(GlacialMacro macro) {
		macro.disable(reason);
	}

	@Override
	public GlacialMacroState onTick(GlacialMacro macro) {
		return this;
	}

	@Override
	public void onEnd(GlacialMacro macro) {
	}
}
