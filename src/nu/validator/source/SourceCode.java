/*
 * Copyright (c) 2007 Mozilla Foundation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL 
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package nu.validator.source;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.xml.sax.SAXException;

import nu.validator.htmlparser.impl.CharacterHandler;

public final class SourceCode implements CharacterHandler {

    private int expectedLength;
    
    private final SortedSet<SourceLocation> reverseSortedLocations = new TreeSet<SourceLocation>(new ReverseSourceLocationComparator());
    
    private final SortedSet<SourceLocation> exactErrors = new TreeSet<SourceLocation>();
    
    private final SortedSet<SourceLocation> rangeEnds = new TreeSet<SourceLocation>();

    private final List<SourceLine> lines = new ArrayList<SourceLine>();

    private SourceLine currentLine = null;

    private boolean prevWasCr = false;

    public void characters(char[] ch, int start, int length)
            throws SAXException {
        int s = start;
        int end = start + length;
        for (int i = start; i < end; i++) {
            char c = ch[i];
            switch (c) {
                case '\r':
                    if (s < i) {
                        currentLine.characters(ch, s, i - s);
                    }
                    newLine();
                    s = i + 1;
                    prevWasCr = true;
                    break;
                case '\n':
                    if (!prevWasCr) {
                        if (s < i) {
                            currentLine.characters(ch, s, i - s);
                        }
                        newLine();
                    }
                    s = i + 1;
                    prevWasCr = false;
                    break;
                default:
                    prevWasCr = false;
                    break;
            }
        }
        if (s < end) {
            currentLine.characters(ch, s, end - s);
        }
    }

    private void newLine() {
        int offset;
        char[] buffer;
        if (currentLine == null) {
            offset = 0;
            buffer = new char[expectedLength];
        } else {
           offset = currentLine.getOffset() + currentLine.getBufferLength();
           buffer = currentLine.getBuffer();
        }
        currentLine = new SourceLine(buffer, offset);
        lines.add(currentLine);
    }

    public void end() throws SAXException {
        if (currentLine.getBufferLength() == 0) {
            // Theoretical impurity with line separators vs. terminators
            lines.remove(lines.size() - 1);
        }
    }

    public void start() throws SAXException {
        reverseSortedLocations.clear();
        lines.clear();
        currentLine = null;
        newLine();
        prevWasCr = false;
    }
    
    void addLocatorLocation(int oneBasedLine, int oneBasedColumn) {
        reverseSortedLocations.add(new SourceLocation(this, oneBasedLine - 1, oneBasedColumn - 1));
    }
    
    public void exactError(int oneBasedLine, int oneBasedColumn, SourceHandler extractHandler) throws SAXException {
        int zeroBasedLine = oneBasedLine - 1;
        int zeroBasedColumn = oneBasedColumn - 1;
        SourceLocation location = new SourceLocation(this, zeroBasedLine, zeroBasedColumn);
        exactErrors.add((SourceLocation) location.clone());
        if (isWithinKnownSource(location)) {
            SourceLocation start = (SourceLocation) location.clone();
            start.move(-15);
            SourceLocation end = (SourceLocation) location.clone();
            end.move(6);
            try {
                extractHandler.start();
                emitContent(start, location, extractHandler);
                extractHandler.startCharHilite(oneBasedLine, oneBasedColumn);
                emitCharacter(location, extractHandler);
                extractHandler.endCharHilite();
                SourceLine line = getLine(location.getLine());
                if (location.getColumn() + 1 == line.getBufferLength()) {
                    extractHandler.newLine();
                }
                location.increment();
                emitContent(location, end, extractHandler);
            } finally {
                extractHandler.end();
            }
        }
    }
    
    public void rangeEndError(int oneBasedLine, int oneBasedColumn, SourceHandler extractHandler) throws SAXException {
        int zeroBasedLine = oneBasedLine - 1;
        int zeroBasedColumn = oneBasedColumn - 1;
        SourceLocation location = new SourceLocation(this, zeroBasedLine, zeroBasedColumn);
        SourceLocation startRange = null;
        for (SourceLocation loc : reverseSortedLocations) {
            if (loc.compareTo(location) < 0) {
                startRange = loc;
                break;
            }
        }
        if (startRange == null) {
            startRange = new SourceLocation(this, 0, 0);
        }
        reverseSortedLocations.add(location);
        rangeEnds.add(location);
        SourceLocation endRange = (SourceLocation) location.clone();
        endRange.increment();
        SourceLocation start = (SourceLocation) startRange.clone();
        start.move(-10);
        SourceLocation end = (SourceLocation) endRange.clone();
        end.move(6);
        try {
            extractHandler.start();
            emitContent(start, startRange, extractHandler);
            extractHandler.startRange(oneBasedLine, oneBasedColumn);
            emitContent(startRange, endRange, extractHandler);
            extractHandler.endRange();
            emitContent(endRange, end, extractHandler);
        } finally {
            extractHandler.end();
        }
    }

    public void lineError(int oneBasedLine, SourceHandler extractHandler) throws SAXException {
        if (oneBasedLine <= lines.size()) {
            SourceLine line = lines.get(oneBasedLine - 1);
            try {
                extractHandler.start();
                extractHandler.characters(line.getBuffer(), line.getOffset(), line.getBufferLength());
            } finally {
                extractHandler.end();
            }
        }
    }
    
    private boolean isWithinKnownSource(SourceLocation location) {
        if (location.getLine() >= lines.size()) {
            return false;
        }
        SourceLine line = lines.get(location.getLine());
        if (line.getBufferLength() > location.getColumn()) {
            return true;
        } else {
            return false;
        }
    }
    
    SourceLine getLine(int line) {
        return lines.get(line);
    }
    
    int getNumberOfLines() {
        return lines.size();
    }
    
    void emitCharacter(SourceLocation location, SourceHandler handler) throws SAXException {
        SourceLine line = getLine(location.getLine());
        handler.characters(line.getBuffer(), line.getOffset() + location.getColumn(), 1);
    }
    
    /**
     * Emits content between from a location (inclusive) until a location (exclusive). 
     * There is no way to point to line breaks. They are always emitted together with 
     * the last character on a line.
     * 
     * @param from
     * @param until
     * @param handler
     * @throws SAXException
     */
    void emitContent(SourceLocation from, SourceLocation until, SourceHandler handler) throws SAXException {
        if (from.compareTo(until) >= 0) {
            return;
        }
        int fromLine = from.getLine();
        int untilLine = until.getLine();
        SourceLine line = getLine(fromLine);
        if (fromLine == untilLine) {
            handler.characters(line.getBuffer(), line.getOffset() + from.getColumn(), until.getColumn() - from.getColumn());
        } else {
            // first line
            int length = line.getBufferLength() - from.getColumn();
            if (length > 0) {
                handler.characters(line.getBuffer(), line.getOffset() + from.getColumn(), length);
            }
            handler.newLine();
            // lines in between
            int wholeLine = fromLine + 1;
            while (wholeLine < untilLine) {
                line = getLine(wholeLine);
                handler.characters(line.getBuffer(), line.getOffset(), line.getBufferLength());
                handler.newLine();
            }
            // last line
            int untilCol = until.getColumn();
            if (untilCol > 0) {
                line = getLine(untilLine);
                handler.characters(line.getBuffer(), line.getOffset(), untilCol);
            }
        }
    }
}