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
 * RationalPoint.java
 *
 * Created on 1. Februar 2003, 13:12
 */

package freert.planar;

import java.math.BigInteger;
import freert.varie.MathAux;

/**
 *
 * Implementation of points in the projective plane represented by 3 coordinates x, y, z, which are infinite precision integers. Two
 * projective points (x1, y1, z1) and (x2, y2 z2) are equal, if they are located on the same line through the zero point, that
 * means, there exist a number r with x2 = r*x1, y2 = r*y1 and z2 = r*z1. The affine Point with rational coordinates represented by
 * the projective Point (x, y, z) is (x/z, y/z). The projective plane with integer coordinates contains in addition to the affine
 * plane with rational coordinates the so-called line at infinity, which consist of all projective points (x, y, z) with z = 0.
 *
 * @author Alfons Wirtz
 */

public final class PlaPointRational extends PlaPoint implements java.io.Serializable
   {
   private static final long serialVersionUID = 1L;
   
   final BigInteger rp_x;
   final BigInteger rp_y;
   final BigInteger rp_z;

   private boolean is_nan;
   
   /**
    * creates a RetionalPoint from 3 BigIntegers p_x, p_y and p_z. 
    * They represent the 2-dimensinal point with the rational number
    * Tuple ( p_x / p_z , p_y / p_z). 
    * Throws IllegalArgumentException if denominator p_z is <= 0
    */
   PlaPointRational(BigInteger p_x, BigInteger p_y, BigInteger p_z)
      {
      rp_x = p_x;
      rp_y = p_y;
      rp_z = p_z;
      
      is_nan = p_z.signum() <= 0;
      }


   @Override 
   public final boolean is_rational()
      {
      return true;
      }

   
   /**
    * approximates the coordinates of this point by float coordinates
    */
   @Override
   public PlaPointFloat to_float()
      {
      double xd = rp_x.doubleValue();
      double yd = rp_y.doubleValue();
      double zd = rp_z.doubleValue();
      
      if ( is_nan )
         {
         xd = Float.MAX_VALUE;
         yd = Float.MAX_VALUE;
         new IllegalArgumentException("RationalPoint: to_float NAN").printStackTrace();
         }
      else
         {
         xd /= zd;
         yd /= zd;
         }

      return new PlaPointFloat(xd, yd);
      }
   
   
   public final PlaPointInt round()
      {
      return to_float().round();
      }

   @Override
   public final boolean is_NaN ()
      {
      return is_nan;
      }
   
   /**
    * returns true, if this RationalPoint is equal to p_ob
    */
   @Override
   public final boolean equals(Object p_ob)
      {

      if ( ! (p_ob instanceof PlaPointRational) ) return false;

      PlaPointRational other = (PlaPointRational) p_ob;

      BigInteger det = MathAux.determinant(rp_x, other.rp_x, rp_z, other.rp_z);
      
      if (det.signum() != 0) return false;

      det = MathAux.determinant(rp_y, other.rp_y, rp_z, other.rp_z);

      return (det.signum() == 0);
      }
   
   @Override
   public final boolean equals(PlaPointInt p_ob)
      {
      return false;
      }

   @Override
   public final boolean equals(PlaPointRational p_ob)
      {
      if ( p_ob == null ) return false;
      
      if (this == p_ob) return true;
      
      BigInteger det = MathAux.determinant(rp_x, p_ob.rp_x, rp_z, p_ob.rp_z);
      
      if (det.signum() != 0) return false;

      det = MathAux.determinant(rp_y, p_ob.rp_y, rp_z, p_ob.rp_z);

      return det.signum() == 0;
      }
   

   @Override
   public PlaPointRational translate_by(PlaVectorInt p_vector)
      {
      BigInteger v1[] = new BigInteger[3];
      v1[0] = rp_x;
      v1[1] = rp_y;
      v1[2] = rp_z;

      BigInteger v2[] = new BigInteger[3];
      v2[0] = BigInteger.valueOf(p_vector.v_x);
      v2[1] = BigInteger.valueOf(p_vector.v_y);
      v2[2] = BigInteger.ONE;
      BigInteger[] result = MathAux.add_rational_coordinates(v1, v2);
      
      return new PlaPointRational(result[0], result[1], result[2]);
      }

   
   /**
    * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from p_1 to p_2; 
    * Side.ON_THE_RIGHT, if this Point is on the right of the line from p_1 to p_2; 
    * Side.COLLINEAR, if this Point is collinear with p_1 and p_2.
    */
   public final PlaSide side_of(PlaPoint p_1, PlaPoint p_2)
      {
//      PlaVector v1 = difference_by(p_1);
//      PlaVector v2 = p_2.difference_by(p_1);
//      PlaSide a_risul = v1.side_of(v2);
      
      // now, another way to calculate this, consider the line p_1 -> p_2 as reference and set origins on p_1
      PlaPointFloat pf_1 = p_1.to_float();
      PlaPointFloat pf_2 = p_2.to_float();

      // use myself as float to do calculation
      PlaPointFloat my_float = to_float();
      
      // center the point to pf_1, and this becomes a "direction"
      PlaPointFloat point_dir = new PlaPointFloat(my_float.v_x - pf_1.v_x, my_float.v_y - pf_1.v_y);
      
      // center the second point to pf_1, and this becomes a "direction" of the line
      PlaPointFloat line_dir = new PlaPointFloat(pf_2.v_x - pf_1.v_x, pf_2.v_y - pf_1.v_y);
      
      double determinant = MathAux.determinant(point_dir, line_dir);
      
      PlaSide b_risul = PlaSide.get_side_of(determinant);
      
 //     if ( a_risul != b_risul )
 //        System.err.println(classname+"a_risul="+a_risul+" b_risul="+b_risul);
      
      return b_risul;
      }
   
   
   @Override
   public PlaSide side_of(PlaLineInt p_line)
      {
      return side_of(p_line.point_a, p_line.point_b);
      }

   /*
//   @Override
   public PlaPoint perpendicular_projection(PlaLineInt p_line)
      {
      // this function is at the moment only implemented for lines consisting of IntPoints.
      PlaVectorInt v = p_line.point_b.difference_by(p_line.point_a);
      BigInteger vxvx = BigInteger.valueOf((long) v.v_x * v.v_x);
      BigInteger vyvy = BigInteger.valueOf((long) v.v_y * v.v_y);
      BigInteger vxvy = BigInteger.valueOf((long) v.v_x * v.v_y);
      BigInteger denominator = vxvx.add(vyvy);
      BigInteger det = BigInteger.valueOf(p_line.point_a.determinant(p_line.point_b));

      BigInteger tmp1 = vxvx.multiply(rp_x);
      BigInteger tmp2 = vxvy.multiply(rp_y);
      tmp1 = tmp1.add(tmp2);
      tmp2 = det.multiply(BigInteger.valueOf(v.v_y));
      tmp2 = tmp2.multiply(rp_z);
      BigInteger proj_x = tmp1.add(tmp2);

      tmp1 = vxvy.multiply(rp_x);
      tmp2 = vyvy.multiply(rp_y);
      tmp1 = tmp1.add(tmp2);
      tmp2 = det.multiply(BigInteger.valueOf(v.v_x));
      tmp2 = tmp2.multiply(rp_z);
      BigInteger proj_y = tmp1.add(tmp2);

      int d_signum = denominator.signum();
      
      if (d_signum == 0)
         {
         new IllegalArgumentException("Should NEVER happen").printStackTrace();
         return new PlaPointRational(proj_x, proj_y, denominator);
         }
      
      if (d_signum < 0)
         {
         denominator = denominator.negate();
         proj_x = proj_x.negate();
         proj_y = proj_y.negate();
         }

      if ((proj_x.mod(denominator)).signum() == 0 && (proj_y.mod(denominator)).signum() == 0)
         {
         proj_x = proj_x.divide(denominator);
         proj_y = proj_y.divide(denominator);
         if (proj_x.abs().compareTo(PlaLimits.CRIT_INT_BIG) <= 0 && proj_y.abs().compareTo(PlaLimits.CRIT_INT_BIG) <= 0)
            {
            return new PlaPointInt(proj_x.intValue(), proj_y.intValue());
            }
         denominator = BigInteger.ONE;
         }
      
      return new PlaPointRational(proj_x, proj_y, denominator);
      }
*/


   @Override
   public int compare_x_y(PlaPointRational p_other)
      {
      BigInteger tmp1 = rp_x.multiply(p_other.rp_z);
      BigInteger tmp2 = p_other.rp_x.multiply(rp_z);
      int risul = tmp1.compareTo(tmp2);
      
      if ( risul != 0 ) return risul;
      
      tmp1 = rp_y.multiply(p_other.rp_z);
      tmp2 = p_other.rp_y.multiply(rp_z);
      return tmp1.compareTo(tmp2);
      }

   @Override
   public int compare_x_y(PlaPointInt p_other)
      {
      BigInteger tmp1 = rp_z.multiply(BigInteger.valueOf(p_other.v_x));
      int risul = rp_x.compareTo(tmp1);
      
      if ( risul != 0 ) return risul;

      tmp1 = rp_z.multiply(BigInteger.valueOf(p_other.v_y));
      return rp_y.compareTo(tmp1);
      }
   
   @Override 
   public final String toString ()
      {
      PlaPointFloat afloat = to_float();
      return afloat.toString();
      }
   }