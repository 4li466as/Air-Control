import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const AirControlApp());
}

class AirControlApp extends StatelessWidget {
  const AirControlApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Air Control',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  static const platform = MethodChannel('com.aliplayz.air_control/service');
  
  bool isCameraGranted = false;
  bool isOverlayGranted = false;
  bool isAccessibilityEnabled = false;
  bool isTracking = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkPermissions();
    checkCrashLog();
  }

  Future<void> checkCrashLog() async {
    try {
      final String? log = await platform.invokeMethod('getCrashLog');
      if (log != null && log.isNotEmpty) {
        if (mounted) {
          showDialog(
            context: context,
            barrierDismissible: false,
            builder: (context) => AlertDialog(
              title: const Text('Previous Crash Log', style: TextStyle(color: Colors.red)),
              content: SingleChildScrollView(
                child: Text(log, style: const TextStyle(fontSize: 12, fontFamily: 'monospace')),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('OK'),
                ),
              ],
            ),
          );
        }
      }
    } catch (e) {
      debugPrint("Failed to get crash log: $e");
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkPermissions();
    }
  }

  Future<void> _checkPermissions() async {
    final camera = await Permission.camera.isGranted;
    final overlay = await Permission.systemAlertWindow.isGranted;
    final accessibility = await platform.invokeMethod<bool>('isAccessibilityEnabled');

    setState(() {
      isCameraGranted = camera;
      isOverlayGranted = overlay;
      isAccessibilityEnabled = accessibility ?? false;
      if (!isAccessibilityEnabled) {
        isTracking = false;
      }
    });
  }

  Future<void> _requestCamera() async {
    await Permission.camera.request();
    _checkPermissions();
  }

  Future<void> _requestOverlay() async {
    await Permission.systemAlertWindow.request();
    _checkPermissions();
  }

  Future<void> _requestAccessibility() async {
    await platform.invokeMethod('openAccessibilitySettings');
  }

  void _toggleTracking() async {
    if (!isCameraGranted || !isOverlayGranted || !isAccessibilityEnabled) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please grant all permissions first.')),
      );
      return;
    }

    if (isTracking) {
      await platform.invokeMethod('stopTracking');
    } else {
      await platform.invokeMethod('startTracking');
    }

    setState(() {
      isTracking = !isTracking;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Air Control'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Permissions',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            ListTile(
              title: const Text('Camera Permission'),
              subtitle: const Text('Required to track hand gestures.'),
              trailing: isCameraGranted
                  ? const Icon(Icons.check_circle, color: Colors.green)
                  : ElevatedButton(
                      onPressed: _requestCamera,
                      child: const Text('Grant'),
                    ),
            ),
            ListTile(
              title: const Text('Draw Over Apps'),
              subtitle: const Text('Required to draw the cursor.'),
              trailing: isOverlayGranted
                  ? const Icon(Icons.check_circle, color: Colors.green)
                  : ElevatedButton(
                      onPressed: _requestOverlay,
                      child: const Text('Grant'),
                    ),
            ),
            ListTile(
              title: const Text('Accessibility Service'),
              subtitle: const Text('Required to inject clicks and drags.'),
              trailing: isAccessibilityEnabled
                  ? const Icon(Icons.check_circle, color: Colors.green)
                  : ElevatedButton(
                      onPressed: _requestAccessibility,
                      child: const Text('Enable'),
                    ),
            ),
            const Spacer(),
            ElevatedButton(
              onPressed: _toggleTracking,
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.all(24),
                backgroundColor: isTracking ? Colors.red : Colors.blue,
                foregroundColor: Colors.white,
              ),
              child: Text(
                isTracking ? 'STOP TRACKING' : 'START TRACKING',
                style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
              ),
            ),
            const SizedBox(height: 48),
          ],
        ),
      ),
    );
  }
}
