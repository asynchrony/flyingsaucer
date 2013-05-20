package org.xhtmlrenderer.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.render.Box;
import org.xhtmlrenderer.render.InlineLayoutBox;
import org.xhtmlrenderer.render.InlineText;
import org.xhtmlrenderer.render.LineBox;
import org.xhtmlrenderer.render.PageBox;
import org.xhtmlrenderer.render.RenderingContext;

public class SpikeUtil {
	public static final int MARGIN_HEIGHT = 2596;
	public static final int PAGE_HEIGHT = 21150;
	public static final int PAGE_HEIGHT_NO_MARGIN = PAGE_HEIGHT - 2 * (MARGIN_HEIGHT);
	public static final int HALF_PAGE_IMAGE_HEIGHT = PAGE_HEIGHT / 2 + 40 - MARGIN_HEIGHT;
	public static final int BOTTOM_HALF_PAGE_IMAGE_HEIGHT = HALF_PAGE_IMAGE_HEIGHT - 160;

	public static void dump(LayoutContext c) {
		Exception ex = new Exception("DUMP stack, not a problem");
		ex.fillInStackTrace();
		System.err.println("DUMP START");
		// ex.printStackTrace();
		try {
			// dump(c.getRootLayer().getMaster(), c);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.err.println("DUMP END");
	}

	public static void dump(Box box, LayoutContext layoutContext) {
		Element elem = box.getElement();
		if (elem != null) {
			System.out.println("ELEMENT: " + elem.toString());
		}

		StringBuffer sb = new StringBuffer();
		RenderingContext renderContext = new RenderingContext(layoutContext.getSharedContext());

		try {
			box.collectText(renderContext, sb);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(box);
		System.out.println("(" + box.getAbsX() + ", " + box.getAbsY() + ", " + box.getWidth() + "," + box.getHeight()
				+ ") " + sb.toString());
		if (box instanceof InlineLayoutBox) {
			InlineLayoutBox inlineBox = (InlineLayoutBox) box;
			handleInlineLayoutBox(inlineBox);
		}
		for (int index = 0; index < box.getChildCount(); index++) {
			Box childBox = box.getChild(index);
			dump(childBox, layoutContext);
		}
	}

	private static void handleInlineLayoutBox(InlineLayoutBox inlineBox) {
		for (int index = 0; index < inlineBox.getInlineChildCount(); index++) {
			Object child = inlineBox.getInlineChild(index);
			if (child instanceof InlineLayoutBox) {
				InlineLayoutBox inlineBoxLayout = (InlineLayoutBox) child;
				System.out.println("(" + inlineBoxLayout.getAbsX() + ", " + inlineBoxLayout.getAbsY() + ", "
						+ inlineBoxLayout.getWidth() + "," + inlineBoxLayout.getHeight() + ") ");
				handleInlineLayoutBox(inlineBoxLayout);
			} else {
				InlineText inlineText = (InlineText) child;
				int x = inlineText.getX();
				Text textNode = inlineText.getTextNode();
				if (textNode != null) {
					System.out.println(x + " TEXT:" + textNode.getTextContent());
				}
			}
		}
	}

	public static void printPage(LineBox line, LayoutContext context) {
		int page = context.getRootLayer().getFirstPage(context, line).getPageNo();
		if (page == 5) {
			System.err.println("Page 5!");
		}
		System.err.println("Line is on page: " + page + " (content)" + line.getTextDecorations());
	}

	public static void moveTextDown(final LayoutContext c) {
		Box rootBox = c.getRootLayer().getMaster();
		System.out.println("Move text down:");
		final List topPageBoxes = new ArrayList();
		final List bottomPageBoxes = new ArrayList();
		visitAll(rootBox, new IBoxVisitor() {
			public void visitBox(Box box) {
				if (box instanceof BlockBox && box.getElement().getNodeName().equals("div")) {
					int pageNumber = getPage(c, box);
					System.out.println("box on page " + pageNumber);
					StringBuffer sb = new StringBuffer();
					RenderingContext renderContext = new RenderingContext(c.getSharedContext());
					try {
						box.collectText(renderContext, sb);
					} catch (IOException e) {
						e.printStackTrace();
					}
					String text = sb.toString();
					if (text.contains("PICTURE_TOP_START")) {
						System.out.println("Found top picture on page " + pageNumber);
						topPageBoxes.add(box);
					}
					if (text.contains("PICTURE_BOTTOM_START")) {
						System.out.println("Found bottom picture on page " + pageNumber);
						bottomPageBoxes.add(box);
					}
				}
			}
		});

		visitAll(rootBox, new IBoxVisitor() {
			private Box findBox(final LayoutContext c, final List listBoxes, int pageNum) {
				for (Iterator it = listBoxes.iterator(); it.hasNext();) {
					Box box = (Box) it.next();
					int boxPageNum = getPage(c, box);
					if (boxPageNum == pageNum) {
						return box;
					}
				}
				return null;
			}

			public void visitBox(Box box) {
				int pageNum = getPage(c, box);
				Box topPageBox = findBox(c, topPageBoxes, pageNum);
				if (topPageBox != null && box.getAbsY() < topPageBox.getAbsY()) {
					box.setAbsY(box.getAbsY() + HALF_PAGE_IMAGE_HEIGHT);
				}
				Box bottomPageBox = findBox(c, bottomPageBoxes, pageNum);
				if (bottomPageBox != null && box.getAbsY() > bottomPageBox.getAbsY() + bottomPageBox.getHeight()) {
					System.out.println("Moving text behind bottom pic:" + box);
					box.setAbsY(box.getAbsY() - HALF_PAGE_IMAGE_HEIGHT);
				}
			}
		});

		// Move top pictures up
		for (Iterator it = topPageBoxes.iterator(); it.hasNext();) {
			Box box = (Box) it.next();
			int newY = (box.getAbsY() / PAGE_HEIGHT_NO_MARGIN) * PAGE_HEIGHT_NO_MARGIN;
			int yDiff = newY - box.getAbsY();
			moveAll(box, yDiff);
		}
		// Move bottom pictures down
		for (Iterator it = bottomPageBoxes.iterator(); it.hasNext();) {
			Box box = (Box) it.next();
			int newY = (box.getAbsY() / PAGE_HEIGHT_NO_MARGIN) * PAGE_HEIGHT_NO_MARGIN + BOTTOM_HALF_PAGE_IMAGE_HEIGHT;
			int yDiff = newY - box.getAbsY();
			moveAll(box, yDiff);
		}
	}

	private static void moveAll(Box box, final int yDiff) {
		visitAll(box, new IBoxVisitor() {
			public void visitBox(Box box) {
				box.setAbsY(box.getAbsY() + yDiff);
			}
		});
	}

	private static int getPage(LayoutContext c, Box box) {
		PageBox page = c.getRootLayer().getFirstPage(c, box);
		return page.getPageNo();
	}

	private static void visitAll(Box box, IBoxVisitor boxVisitor) {
		for (int index = 0; index < box.getChildCount(); index++) {
			Box childBox = box.getChild(index);
			visitAll(childBox, boxVisitor);
		}
		if (box instanceof InlineLayoutBox) {
			InlineLayoutBox inlineBox = (InlineLayoutBox) box;
			for (int index2 = 0; index2 < inlineBox.getInlineChildCount(); index2++) {
				Object child = inlineBox.getInlineChild(index2);
				if (child instanceof InlineLayoutBox) {
					InlineLayoutBox inlineBoxLayout = (InlineLayoutBox) child;
					visitAll(inlineBoxLayout, boxVisitor);
				}
			}
		}
		boxVisitor.visitBox(box);
	}
}
