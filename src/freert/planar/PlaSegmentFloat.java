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
 * FloatLine.java
 *
 * Created on 19. Februar 2004, 07:22
 */

package freert.planar;

import freert.main.Stat;

/**
 * Defines a line in the plane by to FloatPoints. 
 * Calculations with FloatLines are generally not exact. 
 * For that reason collinearity for example is not defined for FloatLines. 
 * If exactness is needed, use the class Line instead.
 *
 * @author Alfons Wirtz
 */
public final class PlaSegmentFloat implements PlaObject
   {
   public final PlaPointFloat point_a;
   public final PlaPointFloat point_b;

   private boolean is_nan=false;
   
   /**
    * Creates a line from two FloatPoints
    * If null points or the same point is given then the segment will be a NaN
    */
   public PlaSegmentFloat(PlaPointFloat p_a, PlaPointFloat p_b)
      {
      point_a = check_input(p_a);
      point_b = check_input(p_b);

      is_nan |= point_a.equals(point_b);
      }
   
   
   private PlaPointFloat check_input ( PlaPointFloat p_float )
      {
      if ( (p_float != null) && (p_float.is_NaN() == false) ) return p_float;

      is_nan = true;

      Stat.instance.userPrintln("PlaSegmentFloat: param", new IllegalArgumentException("constructor param is null"));
      
      return new PlaPointFloat();
      }
   
   @Override
   public final boolean is_NaN ()
      {
      return is_nan;
      }


   /**
    * Returns the FloatLine with swapped end points.
    */
   public PlaSegmentFloat opposite()
      {
      return new PlaSegmentFloat(point_b, point_a);
      }

   public PlaSegmentFloat adjust_direction(PlaSegmentFloat p_other)
      {
      if (point_b.side_of(point_a, p_other.point_a) == p_other.point_b.side_of(point_a, p_other.point_a))  return this;
      
      return opposite();
      }

   /**
    * Calculates the intersection of this line with p_other. 
    * @returns null, if the lines are parallel.
    */
   public PlaPointFloat intersection(PlaSegmentFloat p_other)
      {
      double d1x = point_b.v_x - point_a.v_x;
      double d1y = point_b.v_y - point_a.v_y;
      double d2x = p_other.point_b.v_x - p_other.point_a.v_x;
      double d2y = p_other.point_b.v_y - p_other.point_a.v_y;
      double det_1 = point_a.v_x * point_b.v_y - point_a.v_y * point_b.v_x;
      double det_2 = p_other.point_a.v_x * p_other.point_b.v_y - p_other.point_a.v_y * p_other.point_b.v_x;
      double det = d2x * d1y - d2y * d1x;
      double is_x;
      double is_y;
      
      if (det == 0) return null;

      is_x = (d2x * det_1 - d1x * det_2) / det;
      is_y = (d2y * det_1 - d1y * det_2) / det;

      return new PlaPointFloat(is_x, is_y);
      }

   /**
    * translates the line perpendicular at about p_dist. If p_dist > 0, the line will be translated to the left, else to the right
    */
   public PlaSegmentFloat translate(double p_dist)
      {
      double dx = point_b.v_x - point_a.v_x;
      double dy = point_b.v_y - point_a.v_y;
      double dxdx = dx * dx;
      double dydy = dy * dy;
      double lenght = Math.sqrt(dxdx + dydy);
      PlaPointFloat new_a;
      if (dxdx <= dydy)
         {
         // translate along the x axis
         double rel_x = (p_dist * lenght) / dy;
         new_a = new PlaPointFloat(point_a.v_x - rel_x, point_a.v_y);
         }
      else
         {
         // translate along the y axis
         double rel_y = (p_dist * lenght) / dx;
         new_a = new PlaPointFloat(point_a.v_x, point_a.v_y + rel_y);
         }
      PlaPointFloat new_b = new PlaPointFloat(new_a.v_x + dx, new_a.v_y + dy);
      return new PlaSegmentFloat(new_a, new_b);
      }

   /**
    * Returns the signed distance of this line from p_point. The result will be positive, if the line is on the left of p_point,
    * else negative.
    */
   public double signed_distance(PlaPointFloat p_point)
      {
      double dx = point_b.v_x - point_a.v_x;
      double dy = point_b.v_y - point_a.v_y;
      double det = dy * (p_point.v_x - point_a.v_x) - dx * (p_point.v_y - point_a.v_y);
      // area of the parallelogramm spanned by the 3 points
      double length = Math.sqrt(dx * dx + dy * dy);
      return det / length;
      }

   /**
    * May return a NaN if no projection exist !
    * A projection may not exist if the point is colinear, that is exactly on the line
    * or it may not exist if the segment start and end points are the same
    * It is better to return a NaN than just p_point since it makes detection failere explicit
    * @returns an approximation of the perpensicular projection of p_point onto this line.
    */
   public PlaPointFloat perpendicular_projection(PlaPointFloat p_point)
      {
      double dx = point_b.v_x - point_a.v_x;
      double dy = point_b.v_y - point_a.v_y;
      
      // This just means that we are adealing wiht a point, return a NaN
      if (dx == 0 && dy == 0) return new PlaPointFloat();

      double dxdx = dx * dx;
      double dydy = dy * dy;
      double dxdy = dx * dy;
      double denominator = dxdx + dydy;
      
      // this will return a NaN
      if ( denominator == 0 ) return new PlaPointFloat();
      
      double det = point_a.v_x * point_b.v_y - point_b.v_x * point_a.v_y;

      double x = (p_point.v_x * dxdx + p_point.v_y * dxdy + det * dy) / denominator;
      double y = (p_point.v_x * dxdy + p_point.v_y * dydy - det * dx) / denominator;

      return new PlaPointFloat(x, y);
      }

   /**
    * TODO need to consider that the distance may not exist
    * @return the distance of p_point to the nearest point of this line between this.a and this.b.
    */
   public double segment_distance(PlaPointFloat p_point)
      {
      PlaPointFloat projection = perpendicular_projection(p_point);

      if (projection.is_contained_in_box(point_a, point_b, 0.01))
         return p_point.distance(projection);

      return Math.min(p_point.distance(point_a), p_point.distance(point_b));
      }

   /**
    * Returns the perpendicular projection of p_line_segment onto this oriented line segment, 
    * Returns null, if the projection is empty.
    */
   public PlaSegmentFloat segment_projection(PlaSegmentFloat p_line_segment)
      {
      if (point_b.scalar_product(point_a, p_line_segment.point_a) < 0)
         {
         return null;
         }
      if (point_a.scalar_product(point_b, p_line_segment.point_b) < 0)
         {
         return null;
         }
      PlaPointFloat projected_a;
      if (point_a.scalar_product(point_b, p_line_segment.point_a) < 0)
         {
         projected_a = point_a;
         }
      else
         {
         projected_a = perpendicular_projection(p_line_segment.point_a);
         
         if ( projected_a.is_NaN() ) return null;
         
         if (Math.abs(projected_a.v_x) >= PlaLimits.CRIT_INT || Math.abs(projected_a.v_y) >= PlaLimits.CRIT_INT)
            return null;
         }

      PlaPointFloat projected_b;
      if (point_b.scalar_product(point_a, p_line_segment.point_b) < 0)
         {
         projected_b = point_b;
         }
      else
         {
         projected_b = perpendicular_projection(p_line_segment.point_b);
         }
      
      if ( projected_b.is_NaN() ) return null;
      
      if (Math.abs(projected_b.v_x) >= PlaLimits.CRIT_INT || Math.abs(projected_b.v_y) >= PlaLimits.CRIT_INT) return null;

      return new PlaSegmentFloat(projected_a, projected_b);
      }

   /**
    * Returns the projection of p_line_segment onto this oriented line segment by moving p_line_segment perpendicular into the
    * direction of this line segmant 
    * @returns null, if the projection is empty or p_line_segment.a == p_line_segment.b
    */
   public PlaSegmentFloat segment_projection_2(PlaSegmentFloat p_line_segment)
      {
      if (p_line_segment.point_a.scalar_product(p_line_segment.point_b, point_b) <= 0)
         {
         return null;
         }
      
      if (p_line_segment.point_b.scalar_product(p_line_segment.point_a, point_a) <= 0)
         {
         return null;
         }
      
      PlaPointFloat projected_a;
      if (p_line_segment.point_a.scalar_product(p_line_segment.point_b, point_a) < 0)
         {
         PlaSegmentFloat curr_perpendicular_line = new PlaSegmentFloat(p_line_segment.point_a, p_line_segment.point_b.rotate_90_deg(1, p_line_segment.point_a));
         projected_a = curr_perpendicular_line.intersection(this);
         if (projected_a == null || Math.abs(projected_a.v_x) >= PlaLimits.CRIT_INT || Math.abs(projected_a.v_y) >= PlaLimits.CRIT_INT)
            {
            return null;
            }
         }
      else
         {
         projected_a = point_a;
         }

      PlaPointFloat projected_b;

      if (p_line_segment.point_b.scalar_product(p_line_segment.point_a, point_b) < 0)
         {
         PlaSegmentFloat curr_perpendicular_line = new PlaSegmentFloat(p_line_segment.point_b, p_line_segment.point_a.rotate_90_deg(1, p_line_segment.point_b));
         projected_b = curr_perpendicular_line.intersection(this);
         if (projected_b == null || Math.abs(projected_b.v_x) >= PlaLimits.CRIT_INT || Math.abs(projected_b.v_y) >= PlaLimits.CRIT_INT)
            {
            return null;
            }
         }
      else
         {
         projected_b = point_b;
         }
      
      return new PlaSegmentFloat(projected_a, projected_b);
      }

   /**
    * Shrinks this line on both sides by p_value. The result will contain at least the gravity point of the line.
    */
   public PlaSegmentFloat shrink_segment(double p_offset)
      {
      double dx = point_b.v_x - point_a.v_x;
      double dy = point_b.v_y - point_a.v_y;
      
      if (dx == 0 && dy == 0) return this;

      double length = Math.sqrt(dx * dx + dy * dy);
      double offset = Math.min(p_offset, length / 2);
      PlaPointFloat new_a = new PlaPointFloat(point_a.v_x + (dx * offset) / length, point_a.v_y + (dy * offset) / length);
      double new_length = length - offset;
      PlaPointFloat new_b = new PlaPointFloat(point_a.v_x + (dx * new_length) / length, point_a.v_y + (dy * new_length) / length);
      return new PlaSegmentFloat(new_a, new_b);
      }

   /**
    * Calculates the nearest point on this line to p_from_point between this.a and this.b
    * TODO need to consider that the project may not exist, for whatever reason
    */
   public PlaPointFloat nearest_segment_point(PlaPointFloat p_from_point)
      {
      PlaPointFloat projection = perpendicular_projection(p_from_point);

      if (projection.is_contained_in_box(point_a, point_b, 0.01))
         {
         return projection;
         }
      
      // Now the projection is outside the line segment.

      if (p_from_point.distance_square(point_a) <= p_from_point.distance_square(point_b))
         return point_a;
      else
         return point_b;
      }

   /**
    * Divides this line segment into p_count line segments of nearly equal length. and at most p_max_section_length.
    */
   public PlaSegmentFloat[] divide_segment_into_sections(int p_count)
      {
      if (p_count == 0)
         {
         return new PlaSegmentFloat[0];
         }
      if (p_count == 1)
         {
         PlaSegmentFloat[] result = new PlaSegmentFloat[1];
         result[0] = this;
         return result;
         }
      double line_length = point_b.distance(point_a);
      PlaSegmentFloat[] result = new PlaSegmentFloat[p_count];
      double section_length = line_length / p_count;
      double dx = point_b.v_x - point_a.v_x;
      double dy = point_b.v_y - point_a.v_y;
      PlaPointFloat curr_a = point_a;
      for (int i = 0; i < p_count; ++i)
         {
         PlaPointFloat curr_b;
         if (i == p_count - 1)
            {
            curr_b = point_b;
            }
         else
            {
            double curr_b_dist = (i + 1) * section_length;
            double curr_b_x = point_a.v_x + (dx * curr_b_dist) / line_length;
            double curr_b_y = point_a.v_y + (dy * curr_b_dist) / line_length;
            curr_b = new PlaPointFloat(curr_b_x, curr_b_y);
            }
         result[i] = new PlaSegmentFloat(curr_a, curr_b);
         curr_a = curr_b;
         }
      return result;
      }

   }
