package de.emrion.kaleidoscopeGrilling.station;

import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Umrechnung aus den Block-Entity-Renderern des Mods: deren Pose beginnt an der Blockecke, ein Display steht in der
 * Blockmitte. Rotation und Skalierung werden mitgeführt, die Position ergibt sich aus der Matrix.
 */
public final class Ber {

    private Ber() {
    }

    /**
     * @param pose     Pose wie im Renderer (ab Blockecke), inklusive aller Rotationen und Skalierungen
     * @param rotation alle Rotationen der Pose in derselben Reihenfolge
     * @param scale    Skalierung nach den Rotationen (Achsen des gedrehten Modells)
     */
    public static Transformation transformation(Matrix4f pose, Quaternionf rotation, Vector3f scale) {
        Matrix4f display = new Matrix4f().translation(-0.5f, -0.5f, -0.5f).mul(pose);
        Vector3f translation = display.getTranslation(new Vector3f());
        return new Transformation(translation, rotation, scale, new Quaternionf());
    }

    /** Drehung um die Y-Achse wie Axis.YP.rotationDegrees */
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
