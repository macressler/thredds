/*
 * Copyright 1997-2007 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package ucar.nc2.iosp;

import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import junit.framework.TestCase;

/**
 * @author caron
 * @since Jan 2, 2008
 */
public class TestIndexChunker extends TestCase {

  public TestIndexChunker( String name) {
    super(name);
  }

  public void testFull() throws InvalidRangeException {
    int[] shape = new int[] {123,22,92,12};
    Section section = new Section(shape);
    IndexChunker index = new IndexChunker(shape, section);
    assert index.getTotalNelems() == section.computeSize();
    IndexChunker.Chunk chunk = index.next();
    assert chunk.getNelems() == section.computeSize();
    assert !index.hasNext();
  }

  public void testPart() throws InvalidRangeException {
    int[] full = new int[] {2, 10, 20};
    int[] part = new int[] {2, 5, 20};
    Section section = new Section(part);
    IndexChunker index = new IndexChunker(full, section);
    assert index.getTotalNelems() == section.computeSize();
    IndexChunker.Chunk chunk = index.next();
    assert chunk.getNelems() == section.computeSize()/2;
  }

  public void testChunkerTiled() throws InvalidRangeException {
    Section dataSection = new Section("0:0, 20:39,  0:1353 ");
    Section wantSection = new Section("0:2, 22:3152,0:1350");
    IndexChunkerTiled index = new IndexChunkerTiled(dataSection, wantSection);
    while(index.hasNext()) {
      Layout.Chunk chunk = index.next();
      System.out.println(" "+chunk);
    }
  }

  public void testChunkerTiled2() throws InvalidRangeException {
    Section dataSection = new Section("0:0, 40:59,  0:1353  ");
    Section wantSection = new Section("0:2, 22:3152,0:1350");
    IndexChunkerTiled index = new IndexChunkerTiled(dataSection, wantSection);
    while(index.hasNext()) {
      Layout.Chunk chunk = index.next();
      System.out.println(" "+chunk);
    }
  }

}
