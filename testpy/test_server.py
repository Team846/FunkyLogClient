#!/usr/bin/env python3
"""
FunkyLogClient Test Server

This script provides a testing server for the FunkyLogClient application.
It includes:
- NetworkTables server (NT3/NT4) with simulated SmartDashboard data
- UDP logging server that sends compressed log messages

Usage:
    python test_server.py [--ip IP] [--nt-port NT_PORT] [--udp-port UDP_PORT]

Requirements:
    pip install pyntcore robotpy-wpiutil

Author: Test Server for FunkyLogClient
"""

import socket
import threading
import time
import random
import argparse
import math
from dataclasses import dataclass
from typing import Optional

# Try to import NetworkTables - will work if pyntcore is installed
try:
    import ntcore
    NT_AVAILABLE = True
except ImportError:
    NT_AVAILABLE = False
    print("Warning: pyntcore not installed. NetworkTables server will be disabled.")
    print("Install with: pip install pyntcore")


# ============================================================================
# COMPRESSION UTILITIES (matches UDPClient.java decompression)
# ============================================================================

ENCODING = "\nabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()[]<>|;:',./?~_- "


def compress_message(text: str) -> bytes:
    """
    Compress a string using 6-bit encoding matching the FunkyLogClient format.
    
    The encoding uses 6 bits per character with the ENCODING alphabet.
    Uppercase letters are encoded as: escape sequence (111111) + lowercase letter.
    """
    bits = ""
    
    for char in text:
        lower_char = char.lower()
        
        if char.isupper() and lower_char in ENCODING:
            # Add escape sequence (all 1s = 63 = index out of range)
            bits += "111111"
            # Add the lowercase version
            index = ENCODING.index(lower_char)
            bits += format(index, '06b')
        elif char in ENCODING:
            index = ENCODING.index(char)
            bits += format(index, '06b')
        else:
            # Unknown character - skip or replace with space
            bits += format(ENCODING.index(' '), '06b')
    
    # Pad to make complete bytes
    while len(bits) % 8 != 0:
        bits += '0'
    
    # Convert to bytes
    result = bytearray()
    for i in range(0, len(bits), 8):
        byte_bits = bits[i:i+8]
        result.append(int(byte_bits, 2))
    
    return bytes(result)


def decompress_message(data: bytes) -> str:
    """
    Decompress a message (for testing the compression).
    Matches UDPClient.java decompress() method.
    """
    bits = ''.join(format(b, '08b') for b in data)
    
    cap_next = False
    result = []
    
    for i in range(0, len(bits), 6):
        segment = bits[i:i+6]
        if len(segment) < 6:
            break
        
        try:
            index = int(segment, 2)
            if index < len(ENCODING):
                char = ENCODING[index]
                if cap_next:
                    char = char.upper()
                    cap_next = False
                result.append(char)
            else:
                cap_next = True
        except (ValueError, IndexError):
            cap_next = True
    
    return ''.join(result)


# ============================================================================
# LOG MESSAGE FORMATTING
# ============================================================================

@dataclass
class LogMessage:
    """Represents a log message to send to the client."""
    msg_type: int  # 0=log, 1=warning, 2=error
    sender: str
    content: str
    time: float
    period: int  # 0=disabled, 1=teleop, 2=auton
    period_timestamp: float
    
    def __str__(self) -> str:
        return f"{self.msg_type};{self.sender};{self.content};{self.time};{self.period};{self.period_timestamp}"


# ============================================================================
# UDP LOGGING SERVER
# ============================================================================

class UDPLoggingServer:
    """UDP server that sends compressed log messages to FunkyLogClient."""
    
    def __init__(self, host: str = "0.0.0.0", port: int = 5808):
        self.host = host
        self.port = port
        self.socket: Optional[socket.socket] = None
        self.running = False
        self.clients: dict = {}  # (ip, port) -> last_seen_time
        self.start_time = time.time()
        self.period = 0  # 0=disabled, 1=teleop, 2=auton
        self.period_start_time = time.time()
        self.message_queue: list = []
        self.lock = threading.Lock()
        
    def start(self):
        """Start the UDP server."""
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.socket.bind((self.host, self.port))
        self.socket.settimeout(0.5)
        self.running = True
        
        # Start receiver thread
        self.receiver_thread = threading.Thread(target=self._receive_loop, daemon=True)
        self.receiver_thread.start()
        
        # Start sender thread
        self.sender_thread = threading.Thread(target=self._send_loop, daemon=True)
        self.sender_thread.start()
        
        # Start demo message generator
        self.demo_thread = threading.Thread(target=self._demo_messages, daemon=True)
        self.demo_thread.start()
        
        print(f"[UDP] Logging server started on {self.host}:{self.port}")
        
    def stop(self):
        """Stop the UDP server."""
        self.running = False
        if self.socket:
            self.socket.close()
            
    def _receive_loop(self):
        """Receive keep-alive packets from clients."""
        while self.running:
            try:
                data, addr = self.socket.recvfrom(1024)
                if data == b"~~~":
                    self.clients[addr] = time.time()
                    if addr not in self.clients or time.time() - self.clients.get(addr, 0) > 5:
                        print(f"[UDP] Client connected: {addr}")
            except socket.timeout:
                continue
            except Exception as e:
                if self.running:
                    print(f"[UDP] Receive error: {e}")
                    
    def _send_loop(self):
        """Send queued messages to all connected clients."""
        while self.running:
            # Remove stale clients (no keep-alive for 5 seconds)
            current_time = time.time()
            stale = [addr for addr, last_seen in self.clients.items() 
                     if current_time - last_seen > 5]
            for addr in stale:
                del self.clients[addr]
                print(f"[UDP] Client disconnected: {addr}")
            
            # Send any queued messages
            with self.lock:
                if self.message_queue and self.clients:
                    # Combine multiple messages into one packet
                    combined = "\n".join(str(m) for m in self.message_queue)
                    compressed = compress_message(combined)
                    
                    for addr in self.clients:
                        try:
                            self.socket.sendto(compressed, addr)
                        except Exception as e:
                            print(f"[UDP] Send error to {addr}: {e}")
                    
                    self.message_queue.clear()
            
            time.sleep(0.1)
            
    def queue_message(self, msg_type: int, sender: str, content: str):
        """Queue a log message to be sent to clients."""
        sys_time = time.time() - self.start_time
        period_time = time.time() - self.period_start_time
        
        msg = LogMessage(
            msg_type=msg_type,
            sender=sender,
            content=content,
            time=round(sys_time, 1),
            period=self.period,
            period_timestamp=round(period_time, 1)
        )
        
        with self.lock:
            self.message_queue.append(msg)
            
    def log(self, sender: str, content: str):
        """Send a log message (type 0)."""
        self.queue_message(0, sender, content)
        
    def warning(self, sender: str, content: str):
        """Send a warning message (type 1)."""
        self.queue_message(1, sender, content)
        
    def error(self, sender: str, content: str):
        """Send an error message (type 2)."""
        self.queue_message(2, sender, content)
        
    def set_period(self, period: int):
        """Set the current period (0=disabled, 1=teleop, 2=auton)."""
        if period != self.period:
            self.period = period
            self.period_start_time = time.time()
            period_names = {0: "DISABLED", 1: "TELE-OP", 2: "AUTON"}
            print(f"[UDP] Period changed to: {period_names.get(period, 'UNKNOWN')}")
            
    def _demo_messages(self):
        """Generate demo log messages automatically."""
        senders = ["drivetrain", "intake", "shooter", "vision", "auto", "robot", "climber", "arm", "wrist", "elevator"]
        
        log_messages = [
            "initialized successfully",
            "motor temperature: {}c",
            "encoder reading: {} ticks",
            "target acquired at {} degrees",
            "executing trajectory segment {}",
            "pose updated: x={}, y={}",
            "subsystem ready",
            "command scheduled: move to position {}",
            "waiting for input",
            "battery voltage: {}v",
            "current draw: {}a",
            "loop time: {}ms",
            "odometry updated",
            "pid output: {}",
            "setpoint reached",
            "motor velocity: {} rpm",
            "sensor value: {}",
            "state machine: transitioning",
            "path following: {} waypoints remaining",
            "gyro heading: {} degrees",
        ]
        
        warning_messages = [
            "motor current spike detected: {}a",
            "encoder drift detected: {} ticks",
            "target lost momentarily",
            "battery voltage dropping: {}v",
            "high cpu usage: {}%",
            "communication latency: {}ms",
            "motor temperature elevated: {}c",
            "brownout risk detected",
            "loop overrun: {}ms",
            "can bus utilization high: {}%",
        ]
        
        error_messages = [
            "motor stall detected!",
            "encoder disconnected!",
            "vision target not found!",
            "communication timeout!",
            "limit switch triggered unexpectedly!",
            "can bus error: device {} not found",
            "pid error too large: {}",
            "motor controller fault!",
            "sensor read failure!",
        ]
        
        period_cycle = 0
        message_count = 0
        
        print("[UDP] Auto-generating demo messages...")
        
        while self.running:
            # Cycle through periods every 20 seconds
            period_cycle += 1
            if period_cycle % 200 == 0:  # Every 20 seconds
                new_period = (self.period + 1) % 3
                self.set_period(new_period)
            
            # Always generate messages (they queue up for when clients connect)
            # Generate 1-3 messages per cycle for busy log output
            num_messages = random.randint(1, 3)
            
            for _ in range(num_messages):
                rand = random.random()
                sender = random.choice(senders)
                
                # Random values for message formatting
                vals = (
                    round(random.uniform(0, 360), 1),
                    round(random.uniform(-100, 100), 2),
                    round(random.uniform(11.5, 13.0), 1),
                    random.randint(1, 20),
                    round(random.uniform(0, 50), 1),
                )
                
                if rand < 0.75:  # 75% chance of log message
                    msg = random.choice(log_messages)
                    try:
                        msg = msg.format(*vals)
                    except (IndexError, KeyError):
                        pass
                    self.log(sender, msg)
                    message_count += 1
                    
                elif rand < 0.92:  # 17% chance of warning
                    msg = random.choice(warning_messages)
                    try:
                        msg = msg.format(*vals)
                    except (IndexError, KeyError):
                        pass
                    self.warning(sender, msg)
                    message_count += 1
                    
                else:  # 8% chance of error
                    msg = random.choice(error_messages)
                    try:
                        msg = msg.format(*vals)
                    except (IndexError, KeyError):
                        pass
                    self.error(sender, msg)
                    message_count += 1
            
            # Print status every 50 messages
            if message_count % 50 == 0 and message_count > 0:
                client_count = len(self.clients)
                if client_count > 0:
                    print(f"[UDP] Sent {message_count} messages to {client_count} client(s)")
            
            # Send messages every 0.3-0.8 seconds for realistic robot logging rate
            time.sleep(random.uniform(0.3, 0.8))


# ============================================================================
# SMOOTH RANDOM VALUE GENERATOR
# ============================================================================

class SmoothValue:
    """
    Generates smoothly changing random values that wander within a range.
    Uses exponential smoothing toward randomly changing targets.
    """
    
    def __init__(self, min_val: float, max_val: float, smoothing: float = 0.05, 
                 target_change_rate: float = 0.02):
        self.min_val = min_val
        self.max_val = max_val
        self.smoothing = smoothing  # How fast to approach target (0-1, lower = smoother)
        self.target_change_rate = target_change_rate  # Chance to pick new target each update
        
        # Start at random position
        self.current = random.uniform(min_val, max_val)
        self.target = self.current
        
    def update(self) -> float:
        """Update and return the current value."""
        # Occasionally pick a new random target
        if random.random() < self.target_change_rate:
            self.target = random.uniform(self.min_val, self.max_val)
        
        # Smoothly move toward target
        self.current += (self.target - self.current) * self.smoothing
        
        # Add tiny noise for more natural feel
        noise = random.uniform(-0.001, 0.001) * (self.max_val - self.min_val)
        self.current = max(self.min_val, min(self.max_val, self.current + noise))
        
        return self.current
    
    def get(self) -> float:
        """Get current value without updating."""
        return self.current
    
    def set_target(self, target: float):
        """Manually set the target value."""
        self.target = max(self.min_val, min(self.max_val, target))


# ============================================================================
# NETWORKTABLES SERVER
# ============================================================================

class NetworkTablesServer:
    """NetworkTables server that provides simulated SmartDashboard data."""
    
    def __init__(self, port: int = 1735):
        self.port = port
        self.running = False
        self.inst: Optional['ntcore.NetworkTableInstance'] = None
        self.table = None
        
    def start(self):
        """Start the NetworkTables server."""
        if not NT_AVAILABLE:
            print("[NT] NetworkTables not available (pyntcore not installed)")
            return False
            
        try:
            self.inst = ntcore.NetworkTableInstance.getDefault()
            
            # NetworkTables NT3 uses port 1735 by default
            # The client connects without specifying a port, so it uses the default
            # Start server - pyntcore startServer() binds to all interfaces by default
            self.inst.startServer(port3=self.port, port4=self.port + 1)
            
            print(f"[NT] NetworkTables server started on port {self.port} (NT3) and {self.port + 1} (NT4)")
            if self.port != 1735:
                print(f"[NT] WARNING: Using non-standard port {self.port}. Client expects port 1735 by default.")
            
            # Set server identity (optional, helps with debugging)
            self.inst.setServer("FunkyLogTestServer")
            
            self.table = self.inst.getTable("SmartDashboard")
            self.preferences_table = self.inst.getTable("Preferences")
            self.running = True
            
            # Start data publishing thread
            self.publish_thread = threading.Thread(target=self._publish_data, daemon=True)
            self.publish_thread.start()
            
            print(f"[NT] Server identity: {self.inst.getServer()}")
            print(f"[NT] Publishing to SmartDashboard and Preferences tables")
            return True
            
        except Exception as e:
            print(f"[NT] Failed to start NetworkTables server: {e}")
            import traceback
            traceback.print_exc()
            return False
            
    def stop(self):
        """Stop the NetworkTables server."""
        self.running = False
        if self.inst:
            self.inst.stopServer()
            
    def _publish_data(self):
        """Publish simulated robot data to SmartDashboard and Preferences."""
        # Create publishers for various data types
        if not self.table or not self.preferences_table:
            return
            
        # Robot state
        robot_enabled = self.table.getBooleanTopic("Robot/Enabled").publish()
        robot_mode = self.table.getStringTopic("Robot/Mode").publish()
        match_time = self.table.getDoubleTopic("Robot/MatchTime").publish()
        battery_voltage = self.table.getDoubleTopic("Robot/BatteryVoltage").publish()
        
        # Drivetrain data
        drive_left_speed = self.table.getDoubleTopic("Drivetrain/LeftSpeed").publish()
        drive_right_speed = self.table.getDoubleTopic("Drivetrain/RightSpeed").publish()
        drive_heading = self.table.getDoubleTopic("Drivetrain/Heading").publish()
        robot_x = self.table.getDoubleTopic("Drivetrain/RobotX").publish()
        robot_y = self.table.getDoubleTopic("Drivetrain/RobotY").publish()
        robot_rotation = self.table.getDoubleTopic("Drivetrain/RobotRotation").publish()
        
        # Shooter data
        shooter_rpm = self.table.getDoubleTopic("Shooter/RPM").publish()
        shooter_ready = self.table.getBooleanTopic("Shooter/Ready").publish()
        shooter_target_angle = self.table.getDoubleTopic("Shooter/TargetAngle").publish()
        
        # Intake data
        intake_has_piece = self.table.getBooleanTopic("Intake/HasPiece").publish()
        intake_speed = self.table.getDoubleTopic("Intake/Speed").publish()
        
        # Vision data
        vision_target_valid = self.table.getBooleanTopic("Vision/TargetValid").publish()
        vision_target_x = self.table.getDoubleTopic("Vision/TargetX").publish()
        vision_target_y = self.table.getDoubleTopic("Vision/TargetY").publish()
        vision_target_distance = self.table.getDoubleTopic("Vision/TargetDistance").publish()
        
        # Motor/sensor data
        motor_temp = self.table.getDoubleTopic("Motors/Temperature").publish()
        motor_current = self.table.getDoubleTopic("Motors/Current").publish()
        gyro_rate = self.table.getDoubleTopic("Sensors/GyroRate").publish()
        arm_angle = self.table.getDoubleTopic("Arm/Angle").publish()
        elevator_height = self.table.getDoubleTopic("Elevator/Height").publish()
        
        # Auto selector - use lowercase keys as expected by AutoSelectorWidget
        auto_table = self.table.getSubTable("Auto")
        auto_options_entry = auto_table.getStringArrayTopic("options").publish()
        auto_selected_entry = auto_table.getStringTopic("selected").publish()
        auto_active_entry = auto_table.getStringTopic("active").publish()
        
        # Preferences table - editable values
        pref_max_speed = self.preferences_table.getDoubleTopic("MaxSpeed").publish()
        pref_shooter_rpm = self.preferences_table.getDoubleTopic("ShooterRPM").publish()
        pref_auto_aim = self.preferences_table.getBooleanTopic("AutoAim").publish()
        pref_vision_enabled = self.preferences_table.getBooleanTopic("VisionEnabled").publish()
        pref_intake_speed = self.preferences_table.getDoubleTopic("IntakeSpeed").publish()
        pref_arm_preset_angle = self.preferences_table.getDoubleTopic("ArmPresetAngle").publish()
        pref_elevator_preset = self.preferences_table.getDoubleTopic("ElevatorPreset").publish()
        pref_team_number = self.preferences_table.getIntegerTopic("TeamNumber").publish()
        pref_match_type = self.preferences_table.getStringTopic("MatchType").publish()
        
        # Initialize auto options - these can change over time
        auto_list = ["Left 3 Piece", "Center 2 Piece", "Right 3 Piece", "Just Leave", "Do Nothing"]
        auto_options_entry.set(auto_list)
        current_selected = auto_list[0]
        current_active = auto_list[0]
        auto_selected_entry.set(current_selected)
        auto_active_entry.set(current_active)
        
        # Track when to update auto options
        auto_options_update_counter = 0
        
        # Initialize preferences with default values
        pref_max_speed.set(3.5)
        pref_shooter_rpm.set(4500.0)
        pref_auto_aim.set(True)
        pref_vision_enabled.set(True)
        pref_intake_speed.set(0.8)
        pref_arm_preset_angle.set(45.0)
        pref_elevator_preset.set(0.5)
        pref_team_number.set(846)
        pref_match_type.set("Practice")
        
        # Subscribe to auto/selected to update auto/active when client changes it
        auto_selected_subscriber = auto_table.getStringTopic("selected").subscribe(current_selected)
        last_selected_value = current_selected
        
        # Create smooth value generators for all numeric values
        # These will wander randomly but smoothly within their ranges
        sv_battery = SmoothValue(11.5, 13.0, smoothing=0.02, target_change_rate=0.01)
        sv_left_speed = SmoothValue(-5.0, 5.0, smoothing=0.08, target_change_rate=0.05)
        sv_right_speed = SmoothValue(-5.0, 5.0, smoothing=0.08, target_change_rate=0.05)
        sv_heading = SmoothValue(-180, 180, smoothing=0.03, target_change_rate=0.02)
        sv_robot_x = SmoothValue(0, 16.5, smoothing=0.04, target_change_rate=0.03)
        sv_robot_y = SmoothValue(0, 8.0, smoothing=0.04, target_change_rate=0.03)
        sv_robot_rot = SmoothValue(-180, 180, smoothing=0.05, target_change_rate=0.02)
        
        sv_shooter_rpm = SmoothValue(0, 6000, smoothing=0.06, target_change_rate=0.02)
        sv_shooter_angle = SmoothValue(20, 70, smoothing=0.04, target_change_rate=0.03)
        sv_intake_speed = SmoothValue(0, 1.0, smoothing=0.1, target_change_rate=0.05)
        
        sv_vision_x = SmoothValue(-15, 15, smoothing=0.06, target_change_rate=0.04)
        sv_vision_y = SmoothValue(-10, 10, smoothing=0.06, target_change_rate=0.04)
        sv_vision_dist = SmoothValue(1.0, 6.0, smoothing=0.05, target_change_rate=0.03)
        
        sv_motor_temp = SmoothValue(25, 65, smoothing=0.01, target_change_rate=0.005)
        sv_motor_current = SmoothValue(0, 40, smoothing=0.08, target_change_rate=0.06)
        sv_gyro_rate = SmoothValue(-200, 200, smoothing=0.1, target_change_rate=0.08)
        sv_arm_angle = SmoothValue(-30, 120, smoothing=0.05, target_change_rate=0.02)
        sv_elevator = SmoothValue(0, 1.5, smoothing=0.04, target_change_rate=0.02)
        
        start_time = time.time()
        t = 0
        mode_cycle = 0
        modes = ["Disabled", "Autonomous", "Teleop", "Test"]
        current_mode = 0
        
        print("[NT] Publishing smooth random values to NetworkTables...")
        
        # Subscribe to preferences to detect client changes (with default values)
        # Preferences are static - they only change when the client edits them
        pref_max_speed_sub = self.preferences_table.getDoubleTopic("MaxSpeed").subscribe(3.5)
        pref_shooter_rpm_sub = self.preferences_table.getDoubleTopic("ShooterRPM").subscribe(4500.0)
        pref_intake_speed_sub = self.preferences_table.getDoubleTopic("IntakeSpeed").subscribe(0.8)
        pref_arm_angle_sub = self.preferences_table.getDoubleTopic("ArmPresetAngle").subscribe(45.0)
        pref_elevator_sub = self.preferences_table.getDoubleTopic("ElevatorPreset").subscribe(0.5)
        pref_auto_aim_sub = self.preferences_table.getBooleanTopic("AutoAim").subscribe(True)
        pref_vision_enabled_sub = self.preferences_table.getBooleanTopic("VisionEnabled").subscribe(True)
        pref_team_number_sub = self.preferences_table.getIntegerTopic("TeamNumber").subscribe(846)
        pref_match_type_sub = self.preferences_table.getStringTopic("MatchType").subscribe("Practice")
        
        while self.running:
            t = time.time() - start_time
            
            # Check for client changes to auto/selected and update auto/active accordingly
            try:
                # Check both the entry directly and the subscriber
                new_selected_entry = auto_selected_entry.get()
                new_selected_sub = auto_selected_subscriber.get()
                
                # Use subscriber value if available, otherwise entry value
                new_selected = new_selected_sub if new_selected_sub else new_selected_entry
                
                if new_selected and new_selected != last_selected_value and new_selected in auto_list:
                    # Client changed the selection - update active to match after a short delay
                    # (simulating robot code processing the selection)
                    current_active = new_selected
                    auto_active_entry.set(current_active)
                    last_selected_value = new_selected
                    print(f"[NT] Auto mode changed: {new_selected} -> active")
            except Exception:
                pass
            
            # Check for client changes to preferences and republish them
            # Preferences are static - we just read what the client sets and republish
            try:
                client_max_speed = pref_max_speed_sub.get()
                if client_max_speed is not None:
                    pref_max_speed.set(client_max_speed)
                    
                client_shooter_rpm = pref_shooter_rpm_sub.get()
                if client_shooter_rpm is not None:
                    pref_shooter_rpm.set(client_shooter_rpm)
                    
                client_intake_speed = pref_intake_speed_sub.get()
                if client_intake_speed is not None:
                    pref_intake_speed.set(client_intake_speed)
                    
                client_arm_angle = pref_arm_angle_sub.get()
                if client_arm_angle is not None:
                    pref_arm_preset_angle.set(client_arm_angle)
                    
                client_elevator = pref_elevator_sub.get()
                if client_elevator is not None:
                    pref_elevator_preset.set(client_elevator)
            except Exception:
                pass
            
            # Republish auto options periodically (every 10 seconds) to ensure they're available
            auto_options_update_counter += 1
            if auto_options_update_counter % 200 == 0:  # Every 10 seconds at 20Hz
                auto_options_entry.set(auto_list)
            
            # Cycle through modes every 30 seconds
            mode_cycle += 1
            if mode_cycle % 300 == 0:
                current_mode = (current_mode + 1) % len(modes)
                
            is_enabled = modes[current_mode] != "Disabled"
            
            # Robot state - always update
            robot_enabled.set(is_enabled)
            robot_mode.set(modes[current_mode])
            match_time.set(max(0, 150 - (t % 150)))  # 2.5 minute matches
            battery_voltage.set(round(sv_battery.update(), 2))
            
            # Read and republish preferences (client can edit these, but they stay static)
            # Preferences only change when explicitly set by the client
            try:
                pref_auto_aim.set(pref_auto_aim_sub.get())
                pref_vision_enabled.set(pref_vision_enabled_sub.get())
                pref_team_number.set(int(pref_team_number_sub.get()))
                pref_match_type.set(pref_match_type_sub.get())
            except Exception:
                pass
            
            # Motor/sensor data - always update (smooth wandering)
            motor_temp.set(round(sv_motor_temp.update(), 1))
            motor_current.set(round(sv_motor_current.update(), 1))
            gyro_rate.set(round(sv_gyro_rate.update(), 1))
            arm_angle.set(round(sv_arm_angle.update(), 1))
            elevator_height.set(round(sv_elevator.update(), 3))
            
            if is_enabled:
                # Drivetrain - smooth random movement
                drive_left_speed.set(round(sv_left_speed.update(), 2))
                drive_right_speed.set(round(sv_right_speed.update(), 2))
                drive_heading.set(round(sv_heading.update(), 1))
                robot_x.set(round(sv_robot_x.update(), 3))
                robot_y.set(round(sv_robot_y.update(), 3))
                robot_rotation.set(round(sv_robot_rot.update(), 1))
                
                # Shooter - smooth RPM changes
                shooter_rpm.set(round(sv_shooter_rpm.update(), 0))
                shooter_target_angle.set(round(sv_shooter_angle.update(), 1))
                shooter_ready.set(sv_shooter_rpm.get() > 4000)
                
                # Intake - smooth speed
                intake_speed.set(round(sv_intake_speed.update(), 2))
                intake_has_piece.set(sv_intake_speed.get() < 0.3)
                
                # Vision - smooth target tracking
                vision_target_valid.set(True)
                vision_target_x.set(round(sv_vision_x.update(), 2))
                vision_target_y.set(round(sv_vision_y.update(), 2))
                vision_target_distance.set(round(sv_vision_dist.update(), 2))
                    
            else:
                # Disabled state - values drift toward zero
                sv_left_speed.set_target(0)
                sv_right_speed.set_target(0)
                sv_shooter_rpm.set_target(0)
                sv_intake_speed.set_target(0)
                
                drive_left_speed.set(round(sv_left_speed.update(), 2))
                drive_right_speed.set(round(sv_right_speed.update(), 2))
                shooter_rpm.set(round(sv_shooter_rpm.update(), 0))
                shooter_ready.set(False)
                intake_speed.set(round(sv_intake_speed.update(), 2))
                vision_target_valid.set(False)
            
            time.sleep(0.05)  # 20Hz update rate for smooth values


# ============================================================================
# MAIN SERVER
# ============================================================================

class TestServer:
    """Combined test server for FunkyLogClient."""
    
    def __init__(self, ip: str = "0.0.0.0", nt_port: int = 5810, udp_port: int = 5808):
        self.udp_server = UDPLoggingServer(ip, udp_port)
        self.nt_server = NetworkTablesServer(nt_port)
        
    def start(self):
        """Start all servers."""
        print("=" * 60)
        print("FunkyLogClient Test Server")
        print("=" * 60)
        print()
        
        self.udp_server.start()
        self.nt_server.start()
        
        print()
        print("-" * 60)
        print("Server is running. Press Ctrl+C to stop.")
        print("-" * 60)
        print()
        
    def stop(self):
        """Stop all servers."""
        print("\nShutting down...")
        self.udp_server.stop()
        self.nt_server.stop()
        print("Server stopped.")
        
    def run_interactive(self):
        """Run with interactive command input."""
        self.start()
        
        print("Commands:")
        print("  log <sender> <message>     - Send a log message")
        print("  warn <sender> <message>    - Send a warning message")
        print("  error <sender> <message>   - Send an error message")
        print("  period <0|1|2>             - Set period (0=disabled, 1=teleop, 2=auton)")
        print("  quit                       - Exit the server")
        print()
        
        try:
            while True:
                try:
                    cmd = input("> ").strip()
                    if not cmd:
                        continue
                        
                    parts = cmd.split(maxsplit=2)
                    command = parts[0].lower()
                    
                    if command == "quit" or command == "exit":
                        break
                    elif command == "log" and len(parts) >= 3:
                        self.udp_server.log(parts[1], parts[2])
                        print(f"Sent log: [{parts[1]}] {parts[2]}")
                    elif command == "warn" and len(parts) >= 3:
                        self.udp_server.warning(parts[1], parts[2])
                        print(f"Sent warning: [{parts[1]}] {parts[2]}")
                    elif command == "error" and len(parts) >= 3:
                        self.udp_server.error(parts[1], parts[2])
                        print(f"Sent error: [{parts[1]}] {parts[2]}")
                    elif command == "period" and len(parts) >= 2:
                        try:
                            period = int(parts[1])
                            if 0 <= period <= 2:
                                self.udp_server.set_period(period)
                            else:
                                print("Period must be 0, 1, or 2")
                        except ValueError:
                            print("Invalid period value")
                    else:
                        print("Unknown command or missing arguments")
                        
                except EOFError:
                    break
                    
        except KeyboardInterrupt:
            pass
            
        self.stop()


def main():
    parser = argparse.ArgumentParser(description="FunkyLogClient Test Server")
    parser.add_argument("--ip", default="0.0.0.0", help="IP address to bind to (default: 0.0.0.0)")
    parser.add_argument("--nt-port", type=int, default=1735, help="NetworkTables port (default: 1735, standard NT3 port)")
    parser.add_argument("--udp-port", type=int, default=5808, help="UDP logging port (default: 5808)")
    parser.add_argument("--non-interactive", action="store_true", help="Run without interactive mode")
    
    args = parser.parse_args()
    
    server = TestServer(args.ip, args.nt_port, args.udp_port)
    
    if args.non_interactive:
        server.start()
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            server.stop()
    else:
        server.run_interactive()


if __name__ == "__main__":
    main()

