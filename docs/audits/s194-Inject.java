import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.InputEvent;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

/** Script lines: "d id x y" | "m id x y" | "u id x y" | "s ms"  — multi-touch aware. */
public class Inject {
    public static void main(String[] args) throws Exception {
        Class<?> imc = Class.forName("android.hardware.input.InputManagerGlobal");
        Object im = imc.getMethod("getInstance").invoke(null);
        Method inject = imc.getMethod("injectInputEvent", InputEvent.class, int.class);
        BufferedReader br = new BufferedReader(new FileReader(args[0]));
        LinkedHashMap<Integer, float[]> active = new LinkedHashMap<>();
        long downTime = 0;
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+");
            if (p[0].equals("s")) { Thread.sleep(Long.parseLong(p[1])); continue; }
            int id = Integer.parseInt(p[1]);
            float x = Float.parseFloat(p[2]), y = Float.parseFloat(p[3]);
            int action;
            int idx;
            if (p[0].equals("d")) {
                active.put(id, new float[]{x, y});
                idx = new ArrayList<>(active.keySet()).indexOf(id);
                if (active.size() == 1) { downTime = SystemClock.uptimeMillis(); action = MotionEvent.ACTION_DOWN; }
                else action = MotionEvent.ACTION_POINTER_DOWN | (idx << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            } else if (p[0].equals("m")) {
                active.put(id, new float[]{x, y});
                action = MotionEvent.ACTION_MOVE;
            } else {
                active.put(id, new float[]{x, y});
                idx = new ArrayList<>(active.keySet()).indexOf(id);
                if (active.size() == 1) action = MotionEvent.ACTION_UP;
                else action = MotionEvent.ACTION_POINTER_UP | (idx << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            }
            int n = active.size();
            MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[n];
            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
            int i = 0;
            for (Map.Entry<Integer, float[]> e : active.entrySet()) {
                props[i] = new MotionEvent.PointerProperties(); props[i].id = e.getKey(); props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                coords[i] = new MotionEvent.PointerCoords(); coords[i].x = e.getValue()[0]; coords[i].y = e.getValue()[1]; coords[i].pressure = 1f; coords[i].size = 0.1f;
                i++;
            }
            long now = SystemClock.uptimeMillis();
            MotionEvent ev = MotionEvent.obtain(downTime, now, action, n, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            inject.invoke(im, ev, 2 /* INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH */);
            ev.recycle();
            if (p[0].equals("u")) active.remove(id);
        }
    }
}
