package org.xhtmlrenderer.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.render.Box;
import org.xhtmlrenderer.render.InlineLayoutBox;
import org.xhtmlrenderer.render.PageBox;
import org.xhtmlrenderer.render.RenderingContext;

public class SpikeUtil {
	public static final int MARGIN_HEIGHT = 2596;
	public static final int PAGE_HEIGHT = 21150;
	public static final int PAGE_HEIGHT_NO_MARGIN = PAGE_HEIGHT - 2 * (MARGIN_HEIGHT);
	public static final int HALF_PAGE_IMAGE_HEIGHT = PAGE_HEIGHT / 2 + 40 - MARGIN_HEIGHT;
	public static final int BOTTOM_HALF_PAGE_IMAGE_HEIGHT = HALF_PAGE_IMAGE_HEIGHT - 160;

	public static void moveTextDown(final LayoutContext c) {
		Box rootBox = c.getRootLayer().getMaster();
		final List topPageBoxes = new ArrayList();
		final List bottomPageBoxes = new ArrayList();
		visitAll(rootBox, new IBoxVisitor() {
			public void visitBox(Box box) {
				if (box instanceof BlockBox && box.getElement().getNodeName().equals("div")) {
					int pageNumber = getPage(c, box);
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

		final Map yDiffs = new HashMap();
		// Move top pictures up
		for (Iterator it = topPageBoxes.iterator(); it.hasNext();) {
			Box box = (Box) it.next();
			int newY = (box.getAbsY() / PAGE_HEIGHT_NO_MARGIN) * PAGE_HEIGHT_NO_MARGIN;
			int yDiff = newY - box.getAbsY();
			moveAll(box, yDiff);
			yDiffs.put(box, new Integer(-yDiff));
		}
		// Move bottom pictures down
		for (Iterator it = bottomPageBoxes.iterator(); it.hasNext();) {
			Box box = (Box) it.next();
			int newY = (box.getAbsY() / PAGE_HEIGHT_NO_MARGIN) * PAGE_HEIGHT_NO_MARGIN + BOTTOM_HALF_PAGE_IMAGE_HEIGHT;
			int yDiff = newY - box.getAbsY();
			moveAll(box, yDiff);
		}

		// Move half-lines
		final Map halfLineAdjustments = new HashMap();
		visitAll(rootBox, new IBoxVisitor() {
			public void visitBox(Box box) {
				int pageNum = getPage(c, box);
				Box bottomPageBox = findBox(c, bottomPageBoxes, pageNum);
				if (!boxTopOverlapsWithBox(box, bottomPageBox) && boxTopOverlapsWithBox(bottomPageBox, box)
						&& box != bottomPageBox && !(box instanceof BlockBox)) {
					System.out.println("Moving partially covered text behind bottom pic:" + box);
					int adjustment = bottomPageBox.getAbsY() - box.getAbsY();
					box.setAbsY(box.getAbsY() + HALF_PAGE_IMAGE_HEIGHT + adjustment);
					halfLineAdjustments.put(bottomPageBox, new Integer(adjustment));
				}
			}
		});

		// Moving text and other stuff around the pictures
		visitAll(rootBox, new IBoxVisitor() {

			public void visitBox(Box box) {
				int pageNum = getPage(c, box);
				// top page images
				Box topPageBox = findBox(c, topPageBoxes, pageNum);
				Integer yDiff = (Integer) yDiffs.get(topPageBox);
				// if we had to move the image by more than the image height, say it was a top page image
				// at the bottom of a page, then we move the text by how much the image moved.
				if (topPageBox != null) {
					int amountToMoveText = Math.max(topPageBox.getHeight(), yDiff.intValue());
					if (box != null && box.getAbsY() < topPageBox.getAbsY() + amountToMoveText
							&& box.getAbsY() > topPageBox.getAbsY() && box != topPageBox) {
						System.out.println("Moving text behind top pic:" + box);
						box.setAbsY(box.getAbsY() + HALF_PAGE_IMAGE_HEIGHT);
					}
				}
				// bottom page images
				Box bottomPageBox = findBox(c, bottomPageBoxes, pageNum);
				if (boxTopOverlapsWithBox(box, bottomPageBox) && box != bottomPageBox) {
					Integer adjustment = (Integer) halfLineAdjustments.get(bottomPageBox);
					if (adjustment == null) {
						adjustment = new Integer(0);
					}
					System.out.println("Moving text behind bottom pic:" + box);
					box.setAbsY(box.getAbsY() + HALF_PAGE_IMAGE_HEIGHT + adjustment.intValue());
				}
			}
		});
	}

	private static Box findBox(final LayoutContext c, final List listBoxes, int pageNum) {
		for (Iterator it = listBoxes.iterator(); it.hasNext();) {
			Box box = (Box) it.next();
			int boxPageNum = getPage(c, box);
			if (boxPageNum == pageNum) {
				return box;
			}
		}
		return null;
	}

	private static boolean boxTopOverlapsWithBox(Box box, Box topPageBox) {
		return topPageBox != null && box != null && box.getAbsY() < topPageBox.getAbsY() + topPageBox.getHeight()
				&& box.getAbsY() > topPageBox.getAbsY();
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
