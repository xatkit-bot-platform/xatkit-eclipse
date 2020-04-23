package com.xatkit.language.execution.ui.highlights

import org.eclipse.swt.graphics.RGB
import org.eclipse.xtext.ui.editor.syntaxcoloring.DefaultHighlightingConfiguration
import org.eclipse.xtext.ui.editor.syntaxcoloring.IHighlightingConfigurationAcceptor
import org.eclipse.xtext.ui.editor.utils.TextStyle
import org.eclipse.swt.SWT
import org.eclipse.xtext.xbase.ui.highlighting.XbaseHighlightingConfiguration

class ExecutionHighlightingConfiguration extends XbaseHighlightingConfiguration {
	
	public static final String STATE_ID = "state"
	
	public static final String INTENT_ID = "intent"
	
	public static final String EVENT_ID = "event"
	
	override configure(IHighlightingConfigurationAcceptor acceptor) {
		super.configure(acceptor)
		acceptor.acceptDefaultHighlighting(STATE_ID, "State", stateTextStyle())
		acceptor.acceptDefaultHighlighting(INTENT_ID, "Intent", intentTextStyle())
		acceptor.acceptDefaultHighlighting(EVENT_ID, "Event", eventTextStyle())
	}
	
	protected def TextStyle stateTextStyle() {
		var textStyle = new TextStyle()
		textStyle.setColor(new RGB(0,92,153))
		textStyle.style = SWT.BOLD
		return textStyle
	}
	
	protected def TextStyle intentTextStyle() {
		var textStyle = new TextStyle()
		textStyle.setColor(new RGB(204,0,0))
		textStyle.style = SWT.BOLD
		return textStyle
	}
	
	protected def TextStyle eventTextStyle() {
		var textStyle = new TextStyle()
		textStyle.setColor(new RGB(204,0,0))
		textStyle.style = SWT.BOLD
		return textStyle
	}
	
}