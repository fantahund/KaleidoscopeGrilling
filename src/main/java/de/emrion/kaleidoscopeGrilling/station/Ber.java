package de.emrion.kaleidoscopeGrilling.station;

import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Conversion from the mod's block entity renderers: their pose starts at the block corner, a display stands in the
 * block centre. Rotation and scale are carried along, the position follows from the matrix.
 */
public final class Ber {

    private Ber() {
    }

    /**
     * @param pose     pose as in the renderer (from the block corner), including all rotations and scales
     * @param rotation all rotations of the pose in the same order
     * @param scale    scale after the rotations (axes of the rotated model)
     */
    public static Transformation transformation(Matrix4f pose, Quaternionf rotation, Vector3f scale) {
        Matrix4f display = new Matrix4f().translation(-0.5f, -0.5f, -0.5f).mul(pose);
        Vector3f translation = display.getTranslation(new Vector3f());
        return new Transformation(translation, rotation, scale, new Quaternionf());
    }

    /** Rotation around the Y axis like Axis.YP.rotationDegrees */
    public static Quaternionf y(float degrees) {
        return new Quaternionf().rotationY((float) Math.toRadians(degrees));
    }

    public static Quaternionf x(float degrees) {
        return new Quaternionf().rotationX((float) Math.toRadians(degrees));
    }

    public static Quaternionf z(float degrees) {
        return new Quaternionf().rotationZ((float) Math.toRadians(degrees));
    }
}
