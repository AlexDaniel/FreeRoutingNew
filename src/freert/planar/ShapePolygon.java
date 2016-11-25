/*
 *  Copyright (C) 2014  Alfons Wirtz  
 *   website www.freerouting.net
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License at <http://www.gnu.org/licenses/> 
 *   for more details.
 *
 * PolygonShape.java
 *
 * Created on 13. Juni 2003, 12:12
 */

package freert.planar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

/**
 * Shape described bei a closed polygon of corner points. 
 * The corners are ordered in counterclock sense around the border of the shape. 
 * The corners are normalysed, so that the corner with the lowest y-value comes first. 
 * In case of equal y-value the corner with the lowest x-value comes first.
 *
 * @author Alfons Wirtz
 */
public final class ShapePolygon extends ShapeSegments
   {
   private static final long serialVersionUID = 1L;
   private static final int seed = 99;
   private static Random random_generator = new Random(seed);

   public final ArrayList<PlaPointInt> corners = new ArrayList<PlaPointInt>();

   // the following fields are for storing pre calculated data
   transient private ShapeTileBox precalculated_bounding_box = null;
   transient private ShapeTileOctagon precalculated_bounding_octagon = null;
   transient private ShapeTile[] precalculated_convex_pieces = null;
   
   
   public ShapePolygon(PlaPointIntAlist p_corner_arr)
      {
      this(new Polypoint(p_corner_arr));
      }
   
   
   private ShapePolygon(Polypoint p_polypoint)
      {
      if (p_polypoint.winding_number_after_closing() < 0)
         {
         // the the corners of the polygon are in clockwise sense
         p_polypoint = p_polypoint.revert_corners();
         }
      
      ArrayList<PlaPointInt> curr_corners = p_polypoint.corners();

      int last_corner_no = curr_corners.size() - 1;

      if (last_corner_no > 0)
         {
         if (curr_corners.get(0).equals(curr_corners.get(last_corner_no)))
            {
            // skip last point
            --last_corner_no;
            }
         }

      boolean last_point_collinear = false;

      if (last_corner_no >= 2)
         {
         last_point_collinear = curr_corners.get(last_corner_no).side_of(curr_corners.get(last_corner_no - 1), curr_corners.get(0)) == PlaSide.COLLINEAR;
         }
      if (last_point_collinear)
         {
         // skip last point
         --last_corner_no;
         }

      int first_corner_no = 0;
      
      boolean first_point_collinear = false;

      if (last_corner_no - first_corner_no >= 2)
         {
         first_point_collinear = curr_corners.get(0).side_of(curr_corners.get(1), curr_corners.get(last_corner_no)) == PlaSide.COLLINEAR;
         }

      if (first_point_collinear)
         {
         // skip first point
         ++first_corner_no;
         }
      
      // search the point with the lowest y and then with the lowest x
      int start_corner_no = first_corner_no;
      PlaPointFloat start_corner = curr_corners.get(start_corner_no).to_float();
      for (int index = start_corner_no + 1; index <= last_corner_no; ++index)
         {
         PlaPointFloat curr_corner = curr_corners.get(index).to_float();
         if (curr_corner.v_y < start_corner.v_y || curr_corner.v_y == start_corner.v_y && curr_corner.v_x < start_corner.v_x)
            {
            start_corner_no = index;
            start_corner = curr_corner;
            }
         }
      
      int new_corner_count = last_corner_no - first_corner_no + 1;
      
      corners.ensureCapacity(new_corner_count);
      
      for (int index = start_corner_no; index <= last_corner_no; ++index)
         {
         corners.add( curr_corners.get(index));
         }
      
      for (int i = first_corner_no; i < start_corner_no; ++i)
         {
         corners.add( curr_corners.get(i));
         }

      }


   
   @Override
   public PlaPointInt corner(int p_no)
      {
      // it will throw exception if out of bounds
      return corners.get(p_no);
      }

   @Override
   public int border_line_count()
      {
      return corners.size();
      }

   @Override
   public boolean corner_is_bounded(int p_no)
      {
      return true;
      }

   @Override
   public boolean intersects(PlaShape p_shape)
      {
      return p_shape.intersects(this);
      }

   @Override
   public boolean intersects(ShapeCircle p_circle)
      {
      ShapeTile[] convex_pieces = split_to_convex();
      for (int index = 0; index < convex_pieces.length; ++index)
         {
         if (convex_pieces[index].intersects(p_circle))
            return true;
         }
      return false;
      }

   
   @Override
   public boolean intersects(ShapeTileSimplex p_simplex)
      {
      ShapeTile[] convex_pieces = split_to_convex();
      for (int i = 0; i < convex_pieces.length; ++i)
         {
         if (convex_pieces[i].intersects(p_simplex))
            return true;
         }
      return false;
      }

   @Override
   public boolean intersects(ShapeTileOctagon p_oct)
      {
      ShapeTile[] convex_pieces = split_to_convex();
      for (int i = 0; i < convex_pieces.length; ++i)
         {
         if (convex_pieces[i].intersects(p_oct))
            return true;
         }
      return false;
      }

   @Override
   public boolean intersects(ShapeTileBox p_box)
      {
      ShapeTile[] convex_pieces = split_to_convex();
      for (int i = 0; i < convex_pieces.length; ++i)
         {
         if (convex_pieces[i].intersects(p_box))
            return true;
         }
      return false;
      }

   @Override
   public ArrayList<Polyline> cutout(Polyline p_polyline)
      {
      System.out.println("PolygonShape.cutout not yet implemented");
      return null;
      }

   @Override
   public ShapePolygon enlarge(double p_offset)
      {
      if (p_offset == 0) return this;

      System.out.println("PolygonShape.enlarge not yet implemented");
      return null;
      }

   @Override
   public double border_distance(PlaPointFloat p_point)
      {
      System.out.println("PolygonShape.border_distance not yet implemented");
      return 0;
      }

   @Override
   public double smallest_radius()
      {
      return border_distance(centre_of_gravity());
      }

   @Override
   public boolean contains(PlaPointFloat p_point)
      {
      ShapeTile[] convex_pieces = split_to_convex();
      for (int i = 0; i < convex_pieces.length; ++i)
         {
         if (convex_pieces[i].contains(p_point))
            return true;
         }
      return false;
      }

   @Override
   public boolean contains_inside(PlaPoint p_point)
      {
      if (contains_on_border(p_point))
         {
         return false;
         }
      return !is_outside(p_point);
      }

   @Override
   public boolean is_outside(PlaPoint p_point)
      {
      ShapeTile[] convex_pieces = split_to_convex();
      
      for (int index = 0; index < convex_pieces.length; ++index)
         {
         if (!convex_pieces[index].is_outside(p_point)) return false;
         }
      
      return true;
      }

   @Override
   public boolean contains(PlaPoint p_point)
      {
      return !is_outside(p_point);
      }

   @Override
   public boolean contains_on_border(PlaPoint p_point)
      {
      // System.out.println("PolygonShape.contains_on_edge not yet implemented");
      return false;
      }

   @Override
   public double distance(PlaPointFloat p_point)
      {
      System.out.println("PolygonShape.distance not yet implemented");
      return 0;
      }

   @Override
   public ShapePolygon translate_by(PlaVectorInt p_vector)
      {
      if (p_vector.equals(PlaVectorInt.ZERO)) return this;
      
      PlaPointIntAlist new_corners = new PlaPointIntAlist(border_line_count());
      
      for (int index = 0; index < border_line_count(); ++index)
         new_corners.add( corners.get(index).translate_by(p_vector) );
      
      return new ShapePolygon(new_corners);
      }

   
   @Override
   public ShapeTileRegular bounding_shape()
      {
      return bounding_octagon();
      }

   @Override
   public ShapeTileBox bounding_box()
      {
      if (precalculated_bounding_box != null) return precalculated_bounding_box;

      double llx = Integer.MAX_VALUE;
      double lly = Integer.MAX_VALUE;
      double urx = Integer.MIN_VALUE;
      double ury = Integer.MIN_VALUE;
      for (int index = 0; index < border_line_count(); ++index)
         {
         PlaPointFloat curr = corners.get(index).to_float();
         llx = Math.min(llx, curr.v_x);
         lly = Math.min(lly, curr.v_y);
         urx = Math.max(urx, curr.v_x);
         ury = Math.max(ury, curr.v_y);
         }
      
      PlaPointInt lower_left = new PlaPointInt(Math.floor(llx), Math.floor(lly));
      PlaPointInt upper_right = new PlaPointInt(Math.ceil(urx), Math.ceil(ury));
      precalculated_bounding_box = new ShapeTileBox(lower_left, upper_right);

      return precalculated_bounding_box;
      }

   public ShapeTileOctagon bounding_octagon()
      {
      if (precalculated_bounding_octagon != null) return precalculated_bounding_octagon;
      
      double lx = Integer.MAX_VALUE;
      double ly = Integer.MAX_VALUE;
      double rx = Integer.MIN_VALUE;
      double uy = Integer.MIN_VALUE;
      double ulx = Integer.MAX_VALUE;
      double lrx = Integer.MIN_VALUE;
      double llx = Integer.MAX_VALUE;
      double urx = Integer.MIN_VALUE;
      
      for (int index = 0; index < border_line_count(); ++index)
         {
         PlaPointFloat curr = corners.get(index).to_float();
         lx = Math.min(lx, curr.v_x);
         ly = Math.min(ly, curr.v_y);
         rx = Math.max(rx, curr.v_x);
         uy = Math.max(uy, curr.v_y);

         double tmp = curr.v_x - curr.v_y;
         ulx = Math.min(ulx, tmp);
         lrx = Math.max(lrx, tmp);

         tmp = curr.v_x + curr.v_y;
         llx = Math.min(llx, tmp);
         urx = Math.max(urx, tmp);
         }
      
      precalculated_bounding_octagon = new ShapeTileOctagon(
            Math.floor(lx), 
            Math.floor(ly), 
            Math.ceil(rx), 
            Math.ceil(uy), 
            Math.floor(ulx), 
            Math.ceil(lrx),
            Math.floor(llx), 
            Math.ceil(urx));

      return precalculated_bounding_octagon;
      }

   /**
    * Checks, if every line segment between 2 points of the shape is contained completely in the shape.
    */
   public boolean is_comvex()
      {
      if (border_line_count() <= 2) return true;
      
      PlaPointInt prev_point = corners.get(border_line_count() - 1);
      PlaPointInt curr_point = corners.get(0);
      PlaPointInt next_point = corners.get(1);

      for (int ind = 0; ind < border_line_count(); ++ind)
         {
         if (next_point.side_of(prev_point, curr_point) == PlaSide.ON_THE_RIGHT) return false;
         prev_point = curr_point;
         curr_point = next_point;
         if (ind == border_line_count() - 2)
            next_point = corners.get(0);
         else
            next_point = corners.get(ind + 2);
         }
      // check, if the sum of the interior angles is at most 2 * pi

      PlaLineInt first_line = new PlaLineInt(corners.get(border_line_count() - 1), corners.get(0));
      PlaLineInt curr_line = new PlaLineInt(corners.get(0), corners.get(1));
      PlaDirection first_direction = first_line.direction();
      PlaDirection curr_direction = curr_line.direction();
      long last_det = first_direction.determinant(curr_direction);

      for (int ind2 = 2; ind2 < border_line_count(); ++ind2)
         {
         curr_line = new PlaLineInt(curr_line.point_b, corners.get(ind2));
         curr_direction = curr_line.direction();
         long curr_det = first_direction.determinant(curr_direction);
         if (last_det <= 0 && curr_det > 0)
            return false;
         last_det = curr_det;
         }

      return true;
      }

   public ShapePolygon convex_hull()
      {
      if (border_line_count() <= 2) return this;
      
      PlaPointInt prev_point = corners.get(border_line_count() - 1);
      PlaPointInt curr_point = corners.get(0);
      PlaPointInt next_point;
      
      for (int ind = 0; ind < border_line_count(); ++ind)
         {
         if (ind == border_line_count() - 1)
            next_point = corners.get(0);
         else
            next_point = corners.get(ind + 1);
         
         if (next_point.side_of(prev_point, curr_point) != PlaSide.ON_THE_LEFT)
            {
            // import current points and then skip curr_point
            PlaPointIntAlist new_corners = new PlaPointIntAlist(corners);
            
            new_corners.remove(ind);
            
            ShapePolygon result = new ShapePolygon(new_corners);
            
            return result.convex_hull();
            }
         
         prev_point = curr_point;
         curr_point = next_point;
         }
      
      return this;
      }

   @Override
   public ShapeTile bounding_tile()
      {
      ShapePolygon hull = convex_hull();
      
      int line_count = hull.border_line_count();
      
      PlaLineIntAlist bounding_lines = new PlaLineIntAlist(line_count);
      
      for (int index = 0; index < line_count - 1; ++index)
         {
         bounding_lines.add( new PlaLineInt(hull.corners.get(index), hull.corners.get(index + 1)) );
         }
      
      bounding_lines.add( new PlaLineInt(hull.corners.get(line_count - 1), hull.corners.get(0)) );
      
      return ShapeTile.get_instance(bounding_lines);
      }

   @Override
   public double area()
      {
      if ( ! dimension().is_area() ) return 0;

      // calculate half of the absolute value of
      // x0 (y1 - yn-1) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ xn-1( y0 - yn-2)
      // where xi, yi are the coordinates of the i-th corner of this polygon.

      double result = 0;
      PlaPointFloat prev_corner = corners.get(border_line_count() - 2).to_float();
      PlaPointFloat curr_corner = corners.get(border_line_count() - 1).to_float();
      
      for (int index = 0; index < border_line_count(); ++index)
         {
         PlaPointFloat next_corner = corners.get(index).to_float();
         result += curr_corner.v_x * (next_corner.v_y - prev_corner.v_y);
         prev_corner = curr_corner;
         curr_corner = next_corner;
         }
      result = 0.5 * Math.abs(result);
      return result;
      }

   @Override
   public PlaDimension dimension()
      {
      if (border_line_count() == 0)  return PlaDimension.EMPTY;

      if (border_line_count() == 1)  return PlaDimension.POINT;
      
      if (border_line_count() == 2)  return PlaDimension.LINE;
      
      return PlaDimension.AREA;
      }

   @Override
   public boolean is_bounded()
      {
      return true;
      }

   @Override
   public boolean is_empty()
      {
      return border_line_count() == 0;
      }

   @Override
   public PlaLineInt border_line(int p_no)
      {
      if (p_no < 0 || p_no >= border_line_count())
         {
         System.out.println("PolygonShape.edge_line: p_no out of range");
         return null;
         }

      PlaPointInt next_corner;
      if (p_no == border_line_count() - 1)
         {
         next_corner = corners.get(0);
         }
      else
         {
         next_corner = corners.get(p_no + 1);
         }
      
      return new PlaLineInt(corners.get(p_no), next_corner);
      }

   @Override
   public PlaPointFloat nearest_point_approx(PlaPointFloat p_from_point)
      {
      double min_dist = Double.MAX_VALUE;
      PlaPointFloat result = null;
      ShapeTile[] convex_shapes = split_to_convex();
      for (int i = 0; i < convex_shapes.length; ++i)
         {
         PlaPointFloat curr_nearest_point = convex_shapes[i].nearest_point_approx(p_from_point);
         double curr_dist = curr_nearest_point.distance_square(p_from_point);
         if (curr_dist < min_dist)
            {
            min_dist = curr_dist;
            result = curr_nearest_point;
            }
         }
      return result;
      }

   @Override
   public ShapePolygon rotate_90_deg(int p_factor, PlaPointInt p_pole)
      {
      PlaPointIntAlist new_corners = new PlaPointIntAlist(border_line_count());
      for (int index = 0; index < border_line_count(); ++index)
         {
         new_corners.add( corners.get(index).rotate_90_deg(p_factor, p_pole));
         }
      return new ShapePolygon(new_corners);
      }

   @Override
   public ShapePolygon rotate_rad(double p_angle, PlaPointFloat p_pole)
      {
      if (p_angle == 0) return this;

      PlaPointIntAlist new_corners = new PlaPointIntAlist(border_line_count());
      
      for (int index = 0; index < border_line_count(); ++index)
         {
         new_corners.add( corners.get(index).to_float().rotate_rad(p_angle, p_pole).round());
         }
      
      return new ShapePolygon(new_corners);
      }

   @Override
   public ShapePolygon mirror_vertical(PlaPointInt p_pole)
      {
      PlaPointIntAlist new_corners = new PlaPointIntAlist(border_line_count());
      
      for (int index = 0; index < border_line_count(); ++index)
         {
         new_corners.add( corners.get(index).mirror_vertical(p_pole));
         }
      
      return new ShapePolygon(new_corners);
      }

   @Override
   public ShapePolygon mirror_horizontal(PlaPointInt p_pole)
      {
      PlaPointIntAlist new_corners = new PlaPointIntAlist(border_line_count());
      
      for (int index = 0; index < border_line_count(); ++index)
         {
         new_corners.add( corners.get(index).mirror_horizontal(p_pole));
         }
      
      return new ShapePolygon(new_corners);
      }

   /**
    * Splits this polygon shape into convex pieces. The result is not exact, because rounded intersections of lines are used in the
    * result pieces. It can be made exact, if Polylines are returned instead of Polygons, so that no intersection points are needed
    * in the result.
    */
   @Override
   public ShapeTile[] split_to_convex()
      {
      if ( precalculated_convex_pieces != null) return precalculated_convex_pieces;

      // use a fixed seed to get reproducible result
      random_generator.setSeed(seed);
      
      Collection<ShapePolygon> convex_pieces = split_to_convex_recu();
      if (convex_pieces == null)
         {
         // split failed, maybe the polygon has selfontersections
         return null;
         }
      
      precalculated_convex_pieces = new ShapeTile[convex_pieces.size()];
      Iterator<ShapePolygon> it = convex_pieces.iterator();
      for (int i = 0; i < precalculated_convex_pieces.length; ++i)
         {
         ShapePolygon curr_piece = it.next();
         
         precalculated_convex_pieces[i] = ShapeTile.get_instance(curr_piece.corners);
         }

      return precalculated_convex_pieces;
      }

   /**
    * Create recursive part of split_to_convex. Returns a collection of polygon shape pieces.
    */
   private Collection<ShapePolygon> split_to_convex_recu()
      {
      // start with a hashed corner and search the first concave corner
      int start_corner_no = random_generator.nextInt(border_line_count());
      PlaPointInt curr_corner = corners.get(start_corner_no);
      PlaPointInt prev_corner;
      if (start_corner_no != 0)
         prev_corner = corners.get(start_corner_no - 1);
      else
         prev_corner = corners.get(border_line_count() - 1);

      PlaPointInt next_corner = null;

      // search for the next concave corner from here
      int concave_corner_no = -1;
      for (int i = 0; i < border_line_count(); ++i)
         {
         if (start_corner_no < border_line_count() - 1)
            next_corner = corners.get(start_corner_no + 1);
         else
            next_corner = corners.get(0);
         if (next_corner.side_of(prev_corner, curr_corner) == PlaSide.ON_THE_RIGHT)
            {
            // concave corner found
            concave_corner_no = start_corner_no;
            break;
            }
         prev_corner = curr_corner;
         curr_corner = next_corner;
         start_corner_no = (start_corner_no + 1) % border_line_count();
         }
      Collection<ShapePolygon> result = new LinkedList<ShapePolygon>();
      if (concave_corner_no < 0)
         {
         // no concave corner found, this shape is already convex
         result.add(this);
         return result;
         }
      ShapePolygonDivisionPoint d = new ShapePolygonDivisionPoint(corners, concave_corner_no);
      if (d.projection == null)
         {
         // projection not found, maybe polygon has selfintersections
         return null;
         }

      // construct the result pieces from p_polygon and the division point
      int corner_count = d.corner_no_after_projection - concave_corner_no;

      if (corner_count < 0)
         corner_count += border_line_count();
      
      ++corner_count;
      
      PlaPointIntAlist first_arr = new PlaPointIntAlist(corner_count);
      int corner_ind = concave_corner_no;

      for (int index = 0; index < corner_count - 1; ++index)
         {
         first_arr.add( corners.get(corner_ind));
         corner_ind = (corner_ind + 1) % border_line_count();
         }
      
      first_arr.add( d.projection.round());
      
      ShapePolygon first_piece = new ShapePolygon(first_arr);

      corner_count = concave_corner_no - d.corner_no_after_projection;
      if (corner_count < 0)
         corner_count += border_line_count();
      corner_count += 2;
      
      PlaPointIntAlist last_arr = new PlaPointIntAlist(corner_count);
      last_arr.add( d.projection.round() );

      corner_ind = d.corner_no_after_projection;
      for (int index = 1; index < corner_count; ++index)
         {
         last_arr.add( corners.get(corner_ind));
         corner_ind = (corner_ind + 1) % border_line_count();
         }
      
      ShapePolygon last_piece = new ShapePolygon(last_arr);
      
      Collection<ShapePolygon> c1 = first_piece.split_to_convex_recu();
      
      if (c1 == null) return null;
      
      Collection<ShapePolygon> c2 = last_piece.split_to_convex_recu();
      
      if (c2 == null) return null;
      result.addAll(c1);
      result.addAll(c2);
      return result;
      }


   }
