package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;

public interface CommissionMacroState {
	void onStart(CommissionMacro macro);

	CommissionMacroState onTick(CommissionMacro macro);

	void onEnd(CommissionMacro macro);
}
