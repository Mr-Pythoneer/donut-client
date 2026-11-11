package dev.donut.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Smooth, lit 3D pastry with raised blue glaze and real cylindrical sprinkles, spinning icing-up like on a turntable. */
final class DonutLogo {
    private static final long EPOCH = System.nanoTime();
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("donut", "pipeline/donut_logo"))
                    .withCull(false).build());
    /** About -60 degrees around X: the glaze normal points mostly up (+Y) and partly at the viewer (+Z). */
    private static final double TILT = -1.05;
    private static final List<Face> LARGE = model(96, 36, 74);
    private static final List<Face> SMALL = model(48, 24, 25);

    private DonutLogo() { }

    static void draw(GuiGraphics g, float x, float y, float size, double motionSpeed) {
        if (size <= 0) return;
        double t = motionSpeed == 0 ? 0 : (System.nanoTime() - EPOCH) / 1_000_000_000.0 * motionSpeed;
        double angle = t * .7 - .38;
        // Spin around the donut's own axis, then tilt it so the glaze faces up and slightly toward the viewer.
        Rotation rotation = new Rotation(angle, TILT + .05 * Math.sin(angle * .7), .06 * Math.sin(angle * .5));
        List<Face> model = size >= 45 ? LARGE : SMALL;
        List<Projected> faces = new ArrayList<>(model.size());
        for (Face face : model) {
            float[] xy = new float[8];
            int[] colors = new int[4];
            float depth = 0;
            for (int i = 0; i < 4; i++) {
                Vertex vertex = face.vertices[i];
                Vec point = rotation.apply(vertex.point);
                Vec normal = rotation.apply(vertex.normal);
                float perspective = 4f / (4 - point.z);
                xy[i * 2] = x + size * (.5f + point.x * .46f * perspective);
                xy[i * 2 + 1] = y + size * (.47f - point.y * .46f * perspective);
                colors[i] = shade(vertex.color, normal, vertex.glaze);
                depth += point.z;
            }
            faces.add(new Projected(xy, colors, depth));
        }
        faces.sort(Comparator.comparingDouble(Projected::depth));
        // A soft contact shadow keeps the character visually anchored in the header.
        UiPaint.round(g, x + size * .16f, y + size * .74f, size * .68f, size * .07f,
                size * .03f, UiPaint.alpha(0, .15));
        g.guiRenderState.submitGuiElement(new Mesh(g, x, y, size, faces));
    }

    private static int shade(int color, Vec n, boolean glaze) {
        float light = Math.max(0, -.36f * n.x + .48f * n.y + .8f * n.z);
        float diffuse = .33f + .68f * light;
        float spec = Math.max(0, -.2f * n.x + .3f * n.y + .9327f * n.z);
        spec *= spec; spec *= spec; spec *= spec; spec *= spec; spec *= spec;
        float gloss = spec * (glaze ? .8f : .12f);
        float rim = 1 - Math.abs(n.z);
        rim = rim * rim * rim * .08f;
        int r = Math.clamp(Math.round((color >> 16 & 255) * (diffuse + rim) + 255 * gloss), 0, 255);
        int gr = Math.clamp(Math.round((color >> 8 & 255) * (diffuse + rim) + 240 * gloss), 0, 255);
        int b = Math.clamp(Math.round((color & 255) * (diffuse + rim) + 231 * gloss), 0, 255);
        return UiPaint.withOpacity(0xFF000000 | r << 16 | gr << 8 | b);
    }

    private static List<Face> model(int around, int tube, int sprinkleCount) {
        List<Face> faces = new ArrayList<>();
        for (int u = 0; u < around; u++) {
            for (int v = 0; v < tube; v++) {
                faces.add(new Face(new Vertex[] {
                        pastry(u * Math.TAU / around, v * Math.TAU / tube),
                        pastry((u + 1) * Math.TAU / around, v * Math.TAU / tube),
                        pastry((u + 1) * Math.TAU / around, (v + 1) * Math.TAU / tube),
                        pastry(u * Math.TAU / around, (v + 1) * Math.TAU / tube)}));
            }
        }
        for (int i = 0; i < sprinkleCount; i++) {
            double u = Math.TAU * fraction(i * .61803398875 + .07);
            double v = Math.PI * (.2 + .6 * fraction(i * .754877666 + .3));
            Vertex surface = pastry(u, v);
            Vec n = surface.normal;
            Vec alongRing = new Vec((float) -Math.sin(u), (float) Math.cos(u), 0);
            Vec alongTube = new Vec((float) (-Math.cos(u) * Math.sin(v)),
                    (float) (-Math.sin(u) * Math.sin(v)), (float) Math.cos(v));
            double direction = i * 2.39996323;
            Vec axis = alongRing.mul((float) Math.cos(direction)).add(alongTube.mul((float) Math.sin(direction)));
            Vec side = axis.cross(n);
            Vec center = surface.point.add(n.mul(.018f));
            int color = sprinkleColor(i);
            for (int j = 0; j < 6; j++) {
                double a = Math.TAU * j / 6, b = Math.TAU * (j + 1) / 6;
                Vec na = n.mul((float) Math.cos(a)).add(side.mul((float) Math.sin(a)));
                Vec nb = n.mul((float) Math.cos(b)).add(side.mul((float) Math.sin(b)));
                faces.add(new Face(new Vertex[] {
                        new Vertex(center.add(axis.mul(-.035f)).add(na.mul(.010f)), na, color, true),
                        new Vertex(center.add(axis.mul(.035f)).add(na.mul(.010f)), na, color, true),
                        new Vertex(center.add(axis.mul(.035f)).add(nb.mul(.010f)), nb, color, true),
                        new Vertex(center.add(axis.mul(-.035f)).add(nb.mul(.010f)), nb, color, true)}));
            }
        }
        return List.copyOf(faces);
    }

    private static int sprinkleColor(int i) {
        int[] palette = {0xFFF0B1, 0x7AE5CF, 0xB6A0F5, 0xFFF5F0, 0xFF8FB8, 0xF8D45A};
        return palette[i % palette.length];
    }

    private static Vertex pastry(double u, double v) {
        float cu = (float) Math.cos(u), su = (float) Math.sin(u);
        float cv = (float) Math.cos(v), sv = (float) Math.sin(v);
        boolean glaze = sv > .1 + .14 * Math.sin(9 * u) + .08 * Math.sin(13 * u + .7);
        float tube = .265f + .009f * (float) Math.sin(5 * u) + (glaze ? .012f : 0);
        float radius = .58f + tube * cv;
        Vec normal = new Vec(cu * cv, su * cv, sv);
        float baked = (float) (.5 + .3 * Math.sin(3 * v) + .06 * Math.sin(31 * u + 7 * v));
        int color = glaze ? UiPaint.blend(0x3D7BE0, 0x9CC8FF, .45 + .35 * sv)
                : UiPaint.blend(0xA95B25, 0xF2BF73, Math.clamp(baked, 0, 1));
        return new Vertex(new Vec(cu * radius, su * radius, tube * sv), normal, color, glaze);
    }

    private static double fraction(double v) { return v - Math.floor(v); }
    private record Vertex(Vec point, Vec normal, int color, boolean glaze) { }
    private record Face(Vertex[] vertices) { }
    private record Projected(float[] xy, int[] colors, float depth) { }
    private record Vec(float x, float y, float z) {
        Vec add(Vec b) { return new Vec(x + b.x, y + b.y, z + b.z); }
        Vec mul(float s) { return new Vec(x * s, y * s, z * s); }
        Vec cross(Vec b) { return new Vec(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x); }
    }
    /** Spin about the donut's own axis (model Z), tilt about screen X, then a slight roll about screen Z. */
    private static final class Rotation {
        private final float cs, ss, cx, sx, cz, sz;
        Rotation(double spin, double tilt, double roll) {
            cs = (float) Math.cos(spin); ss = (float) Math.sin(spin);
            cx = (float) Math.cos(tilt); sx = (float) Math.sin(tilt);
            cz = (float) Math.cos(roll); sz = (float) Math.sin(roll);
        }
        Vec apply(Vec p) {
            float x = p.x * cs - p.y * ss, y = p.x * ss + p.y * cs;
            float ty = y * cx - p.z * sx, z = y * sx + p.z * cx;
            return new Vec(x * cz - ty * sz, x * sz + ty * cz, z);
        }
    }

    private static final class Mesh implements GuiElementRenderState {
        private final Matrix3x2fc pose;
        private final ScreenRectangle scissor, bounds;
        private final List<Projected> faces;
        Mesh(GuiGraphics g, float x, float y, float size, List<Projected> faces) {
            pose = new Matrix3x2f(g.pose());
            scissor = g.scissorStack.peek();
            ScreenRectangle rectangle = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y),
                    (int) Math.ceil(size), (int) Math.ceil(size)).transformMaxBounds(pose);
            bounds = scissor == null ? rectangle : scissor.intersection(rectangle);
            this.faces = faces;
        }
        @Override public void buildVertices(VertexConsumer consumer) {
            for (Projected face : faces) for (int i = 0; i < 4; i++) {
                consumer.addVertexWith2DPose(pose, face.xy[i * 2], face.xy[i * 2 + 1]).setColor(face.colors[i]);
            }
        }
        @Override public RenderPipeline pipeline() { return PIPELINE; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override public ScreenRectangle scissorArea() { return scissor; }
        @Override public ScreenRectangle bounds() { return bounds; }
    }
}
