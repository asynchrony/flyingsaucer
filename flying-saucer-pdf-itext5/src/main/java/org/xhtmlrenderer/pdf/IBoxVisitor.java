package org.xhtmlrenderer.pdf;

import org.xhtmlrenderer.render.Box;

public interface IBoxVisitor {
	void visitBox(Box box);
}
