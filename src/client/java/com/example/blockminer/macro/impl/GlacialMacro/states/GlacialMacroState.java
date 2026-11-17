package com.example.blockminer.macro.impl.GlacialMacro.states;

import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;

public interface GlacialMacroState {
	void onStart(GlacialMacro macro);

	GlacialMacroState onTick(GlacialMacro macro);

	void onEnd(GlacialMacro macro);
}
