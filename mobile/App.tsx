import React, { useEffect, useRef, useState } from 'react';
import * as SecureStore from 'expo-secure-store';
import {
  SafeAreaView,
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  StatusBar,
  KeyboardAvoidingView,
  Platform,
  Alert,
  ScrollView,
  ActivityIndicator,
} from 'react-native';

// Local-development address. Keep the phone and laptop on the same Wi-Fi.
// Update this IP if your laptop's Wi-Fi address changes.
const API_BASE_URL = 'http://192.168.29.230:8080';

const STORAGE_KEYS = {
  accessToken: 'secureconnect.accessToken',
  refreshToken: 'secureconnect.refreshToken',
  sessionId: 'secureconnect.sessionId',
  user: 'secureconnect.user',
  pendingPollToken: 'secureconnect.pendingPollToken',
};

type AuthResponse = {
  id?: number;
  username?: string;
  email?: string;
  token?: string;
  refreshToken?: string;
  sessionId?: string;
  sessionStatus?: string;
  message?: string;
  detail?: string;
};

type DeviceLinkCreateResponse = {
  requestId: string;
  pairingCode: string;
  pollToken: string;
  expiresAt: string;
};

type DeviceLinkPollResponse = {
  status: string;
  userId?: number;
  username?: string;
  email?: string;
  sessionId?: string;
  accessToken?: string;
  refreshToken?: string;
  expiresAt?: string;
};

type DeviceSession = {
  sessionId: string;
  deviceName: string;
  primary: boolean;
  status: string;
  createdAt: string;
  lastActiveAt: string;
  currentDevice: boolean;
};

type User = {
  id: number;
  username: string;
};

type Conversation = {
  id: number;
  createdAt?: string;
};

type Message = {
  id: number;
  senderId: number;
  senderUsername: string;
  content: string;
  createdAt: string;
};

type LastMessagePreview = {
  content: string;
  createdAt: string;
};

type HomeTab = 'chats' | 'contacts' | 'settings';

async function readResponse<T>(response: Response): Promise<T> {
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(
      data?.message ||
        data?.detail ||
        `The server returned error ${response.status}.`,
    );
  }
  return data as T;
}

function formatTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function initials(name?: string) {
  return name?.trim()?.charAt(0)?.toUpperCase() || '?';
}

export default function App() {
  // Authentication
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [loggedInUser, setLoggedInUser] = useState<AuthResponse | null>(null);
  const [restoringSession, setRestoringSession] = useState(true);

  // Device linking
  const [showLinkDevice, setShowLinkDevice] = useState(false);
  const [linkingRequest, setLinkingRequest] = useState<DeviceLinkCreateResponse | null>(null);
  const [linkPollToken, setLinkPollToken] = useState<string | null>(null);
  const [linkingBusy, setLinkingBusy] = useState(false);
  const [linkStatus, setLinkStatus] = useState('');
  const [pairingCodeInput, setPairingCodeInput] = useState('');
  const [approvingDevice, setApprovingDevice] = useState(false);
  const [deviceSessions, setDeviceSessions] = useState<DeviceSession[]>([]);
  const [deviceSessionsLoading, setDeviceSessionsLoading] = useState(false);
  const [revokingSessionId, setRevokingSessionId] = useState<string | null>(null);

  // Home / chat state
  const [activeTab, setActiveTab] = useState<HomeTab>('chats');
  const [users, setUsers] = useState<User[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [lastMessageByUser, setLastMessageByUser] = useState<
    Record<number, LastMessagePreview>
  >({});
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [activeConversation, setActiveConversation] =
    useState<Conversation | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [messageText, setMessageText] = useState('');
  const [chatLoading, setChatLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const messageScrollRef = useRef<ScrollView>(null);

  const apiHeaders = (jwt: string) => ({
    Accept: 'application/json',
    'Content-Type': 'application/json',
    Authorization: `Bearer ${jwt}`,
  });

  const saveCredentials = async (
    jwt: string,
    user: AuthResponse,
    activeSessionId?: string,
    refreshToken?: string,
  ) => {
    const safeProfile = {
      id: user.id,
      username: user.username,
      email: user.email,
    };

    await SecureStore.setItemAsync(STORAGE_KEYS.accessToken, jwt);
    await SecureStore.setItemAsync(STORAGE_KEYS.user, JSON.stringify(safeProfile));

    if (activeSessionId) {
      await SecureStore.setItemAsync(STORAGE_KEYS.sessionId, activeSessionId);
    } else {
      await SecureStore.deleteItemAsync(STORAGE_KEYS.sessionId);
    }

    if (refreshToken) {
      await SecureStore.setItemAsync(STORAGE_KEYS.refreshToken, refreshToken);
    } else {
      await SecureStore.deleteItemAsync(STORAGE_KEYS.refreshToken);
    }
  };

  const clearCredentials = async () => {
    await Promise.all(
      Object.values(STORAGE_KEYS).map((key) =>
        SecureStore.deleteItemAsync(key).catch(() => undefined),
      ),
    );
  };

  const loadUsers = async (jwt: string) => {
    setUsersLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/users`, {
        method: 'GET',
        headers: apiHeaders(jwt),
      });
      const data = await readResponse<User[]>(response);
      setUsers(Array.isArray(data) ? data : []);
    } catch (error) {
      Alert.alert(
        'Could not load people',
        error instanceof Error ? error.message : 'Please try again.',
      );
    } finally {
      setUsersLoading(false);
    }
  };

  const loadDeviceSessions = async (jwt = token) => {
    if (!jwt) return;
    setDeviceSessionsLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/sessions`, {
        method: 'GET',
        headers: apiHeaders(jwt),
      });
      const data = await readResponse<DeviceSession[]>(response);
      setDeviceSessions(Array.isArray(data) ? data : []);
    } catch (error) {
      Alert.alert(
        'Could not load linked devices',
        error instanceof Error ? error.message : 'Please try again.',
      );
    } finally {
      setDeviceSessionsLoading(false);
    }
  };

  const loadMessages = async (conversationId: number, jwt = token) => {
    if (!jwt) return;
    setChatLoading(true);
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/messages/conversation/${conversationId}`,
        {
          method: 'GET',
          headers: apiHeaders(jwt),
        },
      );
      const data = await readResponse<Message[]>(response);
      const loadedMessages = Array.isArray(data) ? data : [];
      setMessages(loadedMessages);

      if (selectedUser && loadedMessages.length > 0) {
        const latest = loadedMessages[loadedMessages.length - 1];
        setLastMessageByUser((current) => ({
          ...current,
          [selectedUser.id]: {
            content: latest.content,
            createdAt: latest.createdAt,
          },
        }));
      }
    } catch (error) {
      Alert.alert(
        'Could not load messages',
        error instanceof Error ? error.message : 'Please try again.',
      );
    } finally {
      setChatLoading(false);
    }
  };

  useEffect(() => {
    let mounted = true;

    const restoreSavedSession = async () => {
      let savedToken: string | null = null;
      let parsedUser: AuthResponse | null = null;
      let savedSessionId: string | null = null;

      try {
        const [storedToken, storedUser, storedSessionId] = await Promise.all([
          SecureStore.getItemAsync(STORAGE_KEYS.accessToken),
          SecureStore.getItemAsync(STORAGE_KEYS.user),
          SecureStore.getItemAsync(STORAGE_KEYS.sessionId),
        ]);

        savedToken = storedToken;
        savedSessionId = storedSessionId;
        if (!savedToken || !storedUser) return;

        parsedUser = JSON.parse(storedUser) as AuthResponse;
        const response = await fetch(`${API_BASE_URL}/api/sessions/current`, {
          method: 'GET',
          headers: apiHeaders(savedToken),
        });

        if (response.status === 401 || response.status === 403) {
          await clearCredentials();
          return;
        }

        // Preserve local credentials during temporary server/network failures.
        if (mounted) {
          setToken(savedToken);
          setSessionId(savedSessionId);
          setLoggedInUser(parsedUser);
        }
      } catch {
        // Do not erase valid local credentials just because the backend is offline.
        if (mounted && savedToken && parsedUser) {
          setToken(savedToken);
          setSessionId(savedSessionId);
          setLoggedInUser(parsedUser);
        }
      } finally {
        if (mounted) setRestoringSession(false);
      }
    };

    void restoreSavedSession();
    return () => {
      mounted = false;
    };
    // Restore once when the app opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (token && loggedInUser) {
      void loadUsers(token);
      void loadDeviceSessions(token);
    }
    // Fetch contacts and linked devices after login.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, loggedInUser?.id]);

  const handleContinue = async () => {
    const cleanEmail = email.trim().toLowerCase();

    if (!cleanEmail || !password) {
      Alert.alert('Missing information', 'Please enter your email and password.');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
      Alert.alert('Invalid email', 'Please enter a valid email address.');
      return;
    }
    if (isRegister && !username.trim()) {
      Alert.alert('Missing information', 'Please enter a username.');
      return;
    }
    if (isRegister && password.length < 8) {
      Alert.alert('Weak password', 'Your password must contain at least 8 characters.');
      return;
    }
    if (loading) return;

    setLoading(true);
    try {
      const endpoint = isRegister ? '/api/auth/register' : '/api/auth/login';
      const requestBody = isRegister
        ? { username: username.trim(), email: cleanEmail, password }
        : { email: cleanEmail, password };

      const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestBody),
      });
      const data = await readResponse<AuthResponse>(response);

      if (isRegister) {
        Alert.alert(
          'Account created',
          `Welcome to SecureConnect, ${data.username || username.trim()}! Please sign in.`,
          [
            {
              text: 'OK',
              onPress: () => {
                setIsRegister(false);
                setPassword('');
              },
            },
          ],
        );
      } else {
        if (!data.token) {
          throw new Error('The server responded, but no login token was returned.');
        }
        await saveCredentials(data.token, data, data.sessionId, data.refreshToken);
        setToken(data.token);
        setSessionId(data.sessionId || null);
        setLoggedInUser(data);
        setPassword('');
        setActiveTab('chats');
        setSelectedUser(null);
        setActiveConversation(null);
        setMessages([]);
        setLastMessageByUser({});
        Alert.alert(
          'Login successful',
          `Welcome back, ${data.username || cleanEmail}!`,
        );
      }
    } catch (error) {
      const message =
        error instanceof Error ? error.message : 'An unexpected error occurred.';
      const networkError =
        message.toLowerCase().includes('network request failed') ||
        message.toLowerCase().includes('failed to fetch');

      Alert.alert(
        networkError ? 'Cannot reach backend' : 'Request failed',
        networkError
          ? 'Check that Spring Boot is running on port 8080, your phone and laptop are on the same Wi-Fi, and Windows Firewall allows the connection.'
          : message,
      );
    } finally {
      setLoading(false);
    }
  };

  const createDeviceLinkRequest = async () => {
    if (linkingBusy) return;
    setLinkingBusy(true);
    setLinkStatus('');
    try {
      const response = await fetch(`${API_BASE_URL}/api/device-link/requests`, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          deviceName: Platform.OS === 'android' ? 'Android device' : 'iOS device',
        }),
      });
      const data = await readResponse<DeviceLinkCreateResponse>(response);
      await SecureStore.setItemAsync(STORAGE_KEYS.pendingPollToken, data.pollToken);
      setLinkingRequest(data);
      setLinkPollToken(data.pollToken);
      setLinkStatus('Waiting for approval from your primary device.');
    } catch (error) {
      Alert.alert(
        'Could not start device linking',
        error instanceof Error ? error.message : 'Please try again.',
      );
    } finally {
      setLinkingBusy(false);
    }
  };

  useEffect(() => {
    if (!linkPollToken) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const pollForApproval = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/device-link/poll`, {
          method: 'POST',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ pollToken: linkPollToken }),
        });
        const result = await readResponse<DeviceLinkPollResponse>(response);
        if (cancelled) return;

        if (result.status === 'PENDING') {
          setLinkStatus('Waiting for approval from your primary device.');
          timer = setTimeout(pollForApproval, 3000);
          return;
        }

        if (result.status === 'COMPLETED') {
          if (
            !result.accessToken || !result.sessionId || !result.userId ||
            !result.username || !result.email
          ) {
            setLinkStatus(
              'Approved, but account profile fields are missing from the backend poll response. Update the backend DTO as described in the setup notes.',
            );
            setLinkPollToken(null);
            return;
          }

          const linkedUser: AuthResponse = {
            id: result.userId,
            username: result.username,
            email: result.email,
            token: result.accessToken,
            refreshToken: result.refreshToken,
            sessionId: result.sessionId,
            sessionStatus: 'ACTIVE',
          };

          await saveCredentials(
            result.accessToken,
            linkedUser,
            result.sessionId,
            result.refreshToken,
          );
          if (cancelled) return;

          await SecureStore.deleteItemAsync(STORAGE_KEYS.pendingPollToken).catch(() => undefined);
          setToken(result.accessToken);
          setSessionId(result.sessionId);
          setLoggedInUser(linkedUser);
          setShowLinkDevice(false);
          setLinkingRequest(null);
          setLinkPollToken(null);
          setLinkStatus('');
          setActiveTab('chats');
          Alert.alert('Device linked', 'This device is now linked to your SecureConnect account.');
          return;
        }

        setLinkStatus(`Pairing request ${result.status.toLowerCase()}.`);
        setLinkPollToken(null);
        await SecureStore.deleteItemAsync(STORAGE_KEYS.pendingPollToken).catch(() => undefined);
      } catch (error) {
        if (cancelled) return;
        setLinkStatus(
          error instanceof Error ? `Connection issue: ${error.message}. Retrying...` : 'Connection issue. Retrying...',
        );
        timer = setTimeout(pollForApproval, 5000);
      }
    };

    void pollForApproval();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [linkPollToken]);

  const approveLinkedDevice = async () => {
    const code = pairingCodeInput.trim().toUpperCase();
    if (!token) return;
    if (!/^[A-HJ-NP-Z2-9]{10}$/.test(code)) {
      Alert.alert('Invalid pairing code', 'Enter the 10-character code shown on the new device.');
      return;
    }

    setApprovingDevice(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/device-link/approve`, {
        method: 'POST',
        headers: apiHeaders(token),
        body: JSON.stringify({ pairingCode: code }),
      });
      const data = await readResponse<{ status: string }>(response);
      setPairingCodeInput('');
      if (data.status === 'APPROVED') {
        Alert.alert('Device approved', 'The new device can now complete linking.');
        await loadDeviceSessions(token);
      } else {
        Alert.alert('Pairing not completed', `Request status: ${data.status}`);
      }
    } catch (error) {
      Alert.alert(
        'Could not approve device',
        error instanceof Error ? error.message : 'Please try again.',
      );
    } finally {
      setApprovingDevice(false);
    }
  };

  const revokeLinkedDevice = async (targetSessionId: string) => {
    if (!token || revokingSessionId) return;
    setRevokingSessionId(targetSessionId);
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/sessions/${encodeURIComponent(targetSessionId)}`,
        { method: 'DELETE', headers: apiHeaders(token) },
      );
      if (!response.ok && response.status !== 204) {
        await readResponse<unknown>(response);
      }
      await loadDeviceSessions(token);
      Alert.alert('Device revoked', 'The selected device has been signed out.');
    } catch (error) {
      Alert.alert(
        'Could not revoke device',
        error instanceof Error ? error.message : 'Please try again.',
      );
    } finally {
      setRevokingSessionId(null);
    }
  };

  const startConversation = async (person: User) => {
    if (!token || chatLoading) return;

    setChatLoading(true);
    setSelectedUser(person);
    setMessages([]);
    setMessageText('');

    try {
      const response = await fetch(
        `${API_BASE_URL}/api/conversations?otherUserId=${person.id}`,
        {
          method: 'POST',
          headers: apiHeaders(token),
        },
      );
      const conversation = await readResponse<Conversation>(response);
      setActiveConversation(conversation);

      const messagesResponse = await fetch(
        `${API_BASE_URL}/api/messages/conversation/${conversation.id}`,
        {
          method: 'GET',
          headers: apiHeaders(token),
        },
      );
      const data = await readResponse<Message[]>(messagesResponse);
      const loadedMessages = Array.isArray(data) ? data : [];
      setMessages(loadedMessages);

      if (loadedMessages.length > 0) {
        const latest = loadedMessages[loadedMessages.length - 1];
        setLastMessageByUser((current) => ({
          ...current,
          [person.id]: {
            content: latest.content,
            createdAt: latest.createdAt,
          },
        }));
      }
    } catch (error) {
      setSelectedUser(null);
      setActiveConversation(null);
      Alert.alert(
        'Could not open chat',
        error instanceof Error ? error.message : 'Please try again.',
      );
    } finally {
      setChatLoading(false);
    }
  };

  const sendMessage = async () => {
    const cleanMessage = messageText.trim();
    if (
      !cleanMessage ||
      !token ||
      !activeConversation ||
      !selectedUser ||
      sending
    ) {
      return;
    }

    setSending(true);
    try {
      const url =
        `${API_BASE_URL}/api/messages?conversationId=${activeConversation.id}` +
        `&content=${encodeURIComponent(cleanMessage)}`;

      const response = await fetch(url, {
        method: 'POST',
        headers: apiHeaders(token),
      });
      const sentMessage = await readResponse<Message>(response);
      setMessages((current) => [...current, sentMessage]);
      setLastMessageByUser((current) => ({
        ...current,
        [selectedUser.id]: {
          content: sentMessage.content,
          createdAt: sentMessage.createdAt,
        },
      }));
      setMessageText('');
    } catch (error) {
      Alert.alert(
        'Message not sent',
        error instanceof Error ? error.message : 'Please try again.',
      );
    } finally {
      setSending(false);
    }
  };

  const goBackToHome = () => {
    setSelectedUser(null);
    setActiveConversation(null);
    setMessages([]);
    setMessageText('');
  };

  const handleLogout = async () => {
    const currentSessionId =
      sessionId || deviceSessions.find((device) => device.currentDevice)?.sessionId;
    try {
      if (token && currentSessionId) {
        await fetch(
          `${API_BASE_URL}/api/sessions/${encodeURIComponent(currentSessionId)}`,
          { method: 'DELETE', headers: apiHeaders(token) },
        );
      }
    } catch {
      // Always clear local credentials even if the backend is unreachable.
    }

    await clearCredentials();
    setToken(null);
    setSessionId(null);
    setLoggedInUser(null);
    setEmail('');
    setPassword('');
    setUsername('');
    setIsRegister(false);
    setUsers([]);
    setDeviceSessions([]);
    setSelectedUser(null);
    setActiveConversation(null);
    setMessages([]);
    setMessageText('');
    setSearchQuery('');
    setLastMessageByUser({});
    setActiveTab('chats');
  };

  const filteredUsers = users.filter((person) =>
    person.username.toLowerCase().includes(searchQuery.trim().toLowerCase()),
  );

  if (restoringSession) {
    return (
      <SafeAreaView style={styles.authSafeArea}>
        <StatusBar barStyle="light-content" />
        <View style={styles.restoreLoading}>
          <ActivityIndicator color="#FFFFFF" size="large" />
          <Text style={styles.restoreLoadingText}>Opening SecureConnect...</Text>
        </View>
      </SafeAreaView>
    );
  }

  // -------------------- CHAT SCREEN --------------------
  if (token && loggedInUser && selectedUser && activeConversation) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <StatusBar barStyle="light-content" />
        <KeyboardAvoidingView
          style={styles.flex}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
          keyboardVerticalOffset={Platform.OS === 'ios' ? 10 : 0}
        >
          <View style={styles.chatHeader}>
            <Pressable onPress={goBackToHome} style={styles.backButton}>
              <Text style={styles.backButtonText}>‹</Text>
            </Pressable>
            <View style={styles.smallAvatar}>
              <Text style={styles.avatarInitial}>{initials(selectedUser.username)}</Text>
            </View>
            <View style={styles.chatHeaderInfo}>
              <Text style={styles.chatTitle}>{selectedUser.username}</Text>
              <Text style={styles.chatSubtitle}>SecureConnect</Text>
            </View>
            <Pressable
              onPress={() => void loadMessages(activeConversation.id)}
              style={styles.headerAction}
            >
              <Text style={styles.headerActionText}>↻</Text>
            </Pressable>
          </View>

          <ScrollView
            ref={messageScrollRef}
            style={styles.messagesArea}
            contentContainerStyle={styles.messagesContent}
            keyboardShouldPersistTaps="handled"
            onContentSizeChange={() =>
              messageScrollRef.current?.scrollToEnd({ animated: true })
            }
          >
            {chatLoading && messages.length === 0 ? (
              <View style={styles.chatLoading}>
                <ActivityIndicator color="#128C7E" size="large" />
                <Text style={styles.emptyMessageText}>Loading messages...</Text>
              </View>
            ) : messages.length === 0 ? (
              <View style={styles.emptyChatCard}>
                <Text style={styles.emptyChatTitle}>Say hello 👋</Text>
                <Text style={styles.emptyChatText}>
                  This is the beginning of your conversation with {selectedUser.username}.
                </Text>
              </View>
            ) : (
              messages.map((message) => {
                const mine = message.senderId === loggedInUser.id;
                return (
                  <View
                    key={message.id}
                    style={[
                      styles.messageBubble,
                      mine ? styles.myMessage : styles.theirMessage,
                    ]}
                  >
                    {!mine && (
                      <Text style={styles.messageSender}>
                        {message.senderUsername}
                      </Text>
                    )}
                    <Text style={styles.messageText}>{message.content}</Text>
                    <Text style={styles.messageTime}>
                      {formatTime(message.createdAt)}
                    </Text>
                  </View>
                );
              })
            )}
          </ScrollView>

          <View style={styles.composer}>
            <TextInput
              style={styles.messageInput}
              placeholder="Message"
              placeholderTextColor="#7A858B"
              value={messageText}
              onChangeText={setMessageText}
              multiline
              editable={!sending}
            />
            <Pressable
              style={[
                styles.sendButton,
                (!messageText.trim() || sending) && styles.sendButtonDisabled,
              ]}
              onPress={() => void sendMessage()}
              disabled={!messageText.trim() || sending}
            >
              {sending ? (
                <ActivityIndicator color="#FFFFFF" size="small" />
              ) : (
                <Text style={styles.sendButtonText}>➤</Text>
              )}
            </Pressable>
          </View>
          <Text style={styles.encryptionNotice}>
            End-to-end encryption is not enabled yet.
          </Text>
        </KeyboardAvoidingView>
      </SafeAreaView>
    );
  }

  // -------------------- HOME / CONTACTS / SETTINGS --------------------
  if (token && loggedInUser) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <StatusBar barStyle="light-content" />

        <View style={styles.homeHeader}>
          <View>
            <Text style={styles.homeBrand}>SecureConnect</Text>
            <Text style={styles.homeSubtitle}>Stay connected with your people</Text>
          </View>
          <Pressable
            style={styles.newChatButton}
            onPress={() => setActiveTab('contacts')}
          >
            <Text style={styles.newChatButtonText}>＋</Text>
          </Pressable>
        </View>

        {activeTab !== 'settings' && (
          <View style={styles.searchWrap}>
            <Text style={styles.searchIcon}>⌕</Text>
            <TextInput
              style={styles.searchInput}
              placeholder="Search people"
              placeholderTextColor="#7A858B"
              value={searchQuery}
              onChangeText={setSearchQuery}
              autoCapitalize="none"
              autoCorrect={false}
            />
            {searchQuery.length > 0 && (
              <Pressable onPress={() => setSearchQuery('')}>
                <Text style={styles.clearSearch}>×</Text>
              </Pressable>
            )}
          </View>
        )}

        {activeTab === 'settings' ? (
          <ScrollView contentContainerStyle={styles.settingsContent}>
            <Text style={styles.screenTitle}>Settings</Text>
            <View style={styles.profileCard}>
              <View style={styles.profileAvatar}>
                <Text style={styles.profileAvatarText}>
                  {initials(loggedInUser.username)}
                </Text>
              </View>
              <View style={styles.profileInfo}>
                <Text style={styles.profileName}>
                  {loggedInUser.username || 'SecureConnect user'}
                </Text>
                <Text style={styles.profileEmail}>{loggedInUser.email || email}</Text>
              </View>
            </View>

            <View style={styles.settingsCard}>
              <Text style={styles.settingsLabel}>ACCOUNT</Text>
              <Text style={styles.settingsItem}>Signed in to SecureConnect</Text>
              <Text style={styles.settingsDetail}>
                Your account is authenticated with the SecureConnect backend.
              </Text>
              <View style={styles.settingsDivider} />
              <Text style={styles.settingsLabel}>SECURITY</Text>
              <Text style={styles.settingsItem}>Encryption status</Text>
              <Text style={styles.settingsDetail}>
                End-to-end encryption is still under development. Do not use this
                development build for confidential messages.
              </Text>
            </View>

            <View style={styles.settingsCard}>
              <Text style={styles.settingsLabel}>DEVICE MANAGEMENT</Text>
              <Text style={styles.settingsItem}>Approve a new device</Text>
              <Text style={styles.settingsDetail}>
                Enter the temporary pairing code displayed on the device you want to link.
              </Text>
              <TextInput
                style={styles.deviceCodeInput}
                placeholder="10-character pairing code"
                placeholderTextColor="#8B969B"
                value={pairingCodeInput}
                onChangeText={(value) =>
                  setPairingCodeInput(
                    value.toUpperCase().replace(/[^A-HJ-NP-Z2-9]/g, '').slice(0, 10),
                  )
                }
                autoCapitalize="characters"
                autoCorrect={false}
                maxLength={10}
              />
              <Pressable
                style={[styles.deviceActionButton, approvingDevice && styles.deviceActionDisabled]}
                onPress={() => void approveLinkedDevice()}
                disabled={approvingDevice || !pairingCodeInput.trim()}
              >
                {approvingDevice ? (
                  <ActivityIndicator color="#FFFFFF" size="small" />
                ) : (
                  <Text style={styles.deviceActionButtonText}>Approve device</Text>
                )}
              </Pressable>

              <View style={styles.settingsDivider} />
              <View style={styles.deviceListHeading}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.settingsItem}>Linked devices</Text>
                  <Text style={styles.settingsDetail}>Review active devices and revoke access.</Text>
                </View>
                <Pressable
                  style={styles.refreshSmallButton}
                  onPress={() => void loadDeviceSessions(token)}
                  disabled={deviceSessionsLoading}
                >
                  <Text style={styles.refreshSmallButtonText}>
                    {deviceSessionsLoading ? '…' : 'Refresh'}
                  </Text>
                </Pressable>
              </View>

              {deviceSessionsLoading && deviceSessions.length === 0 ? (
                <ActivityIndicator color="#128C7E" style={{ marginTop: 14 }} />
              ) : deviceSessions.length === 0 ? (
                <Text style={styles.settingsDetail}>No device sessions found.</Text>
              ) : (
                deviceSessions.map((device) => {
                  const currentIsPrimary = deviceSessions.some(
                    (item) => item.currentDevice && item.primary,
                  );
                  const canRevoke =
                    currentIsPrimary && !device.primary &&
                    device.status === 'ACTIVE' && !device.currentDevice;
                  return (
                    <View key={device.sessionId} style={styles.deviceRow}>
                      <View style={styles.deviceRowInfo}>
                        <Text style={styles.deviceRowName}>
                          {device.deviceName}{device.currentDevice ? ' · This device' : ''}
                        </Text>
                        <Text style={styles.deviceRowMeta}>
                          {device.primary ? 'Primary' : 'Secondary'} · {device.status}
                        </Text>
                      </View>
                      {canRevoke && (
                        <Pressable
                          style={styles.revokeButton}
                          onPress={() => void revokeLinkedDevice(device.sessionId)}
                          disabled={revokingSessionId === device.sessionId}
                        >
                          {revokingSessionId === device.sessionId ? (
                            <ActivityIndicator color="#B13B36" size="small" />
                          ) : (
                            <Text style={styles.revokeButtonText}>Revoke</Text>
                          )}
                        </Pressable>
                      )}
                    </View>
                  );
                })
              )}
            </View>

            <Pressable style={styles.signOutButton} onPress={() => void handleLogout()}>
              <Text style={styles.signOutButtonText}>Sign out</Text>
            </Pressable>
            <Text style={styles.versionText}>SECURECONNECT · ANDROID</Text>
          </ScrollView>
        ) : (
          <View style={styles.homeContent}>
            <View style={styles.listHeadingRow}>
              <View>
                <Text style={styles.screenTitle}>
                  {activeTab === 'chats' ? 'Chats' : 'Contacts'}
                </Text>
                <Text style={styles.listSubtitle}>
                  {activeTab === 'chats'
                    ? 'Start or continue a conversation'
                    : 'People using SecureConnect'}
                </Text>
              </View>
              <Pressable
                onPress={() => token && void loadUsers(token)}
                style={styles.refreshSmallButton}
                disabled={usersLoading}
              >
                <Text style={styles.refreshSmallButtonText}>
                  {usersLoading ? '…' : 'Refresh'}
                </Text>
              </Pressable>
            </View>

            {usersLoading && users.length === 0 ? (
              <View style={styles.homeLoading}>
                <ActivityIndicator color="#128C7E" size="large" />
                <Text style={styles.emptyMessageText}>Loading people...</Text>
              </View>
            ) : filteredUsers.length === 0 ? (
              <View style={styles.emptyPeople}>
                <Text style={styles.emptyPeopleTitle}>
                  {users.length === 0 ? 'No other people yet' : 'No matching people'}
                </Text>
                <Text style={styles.emptyMessageText}>
                  {users.length === 0
                    ? 'Create another test account to try messaging between two users.'
                    : 'Try searching with a different name.'}
                </Text>
              </View>
            ) : (
              <ScrollView
                contentContainerStyle={styles.userList}
                keyboardShouldPersistTaps="handled"
              >
                {filteredUsers.map((person) => {
                  const preview = lastMessageByUser[person.id];
                  return (
                    <Pressable
                      key={person.id}
                      style={({ pressed }) => [
                        styles.userRow,
                        pressed && styles.userRowPressed,
                      ]}
                      onPress={() => void startConversation(person)}
                      disabled={chatLoading}
                    >
                      <View style={styles.personAvatar}>
                        <Text style={styles.personAvatarText}>
                          {initials(person.username)}
                        </Text>
                      </View>
                      <View style={styles.personTextBlock}>
                        <View style={styles.personNameLine}>
                          <Text style={styles.personName}>{person.username}</Text>
                          <Text style={styles.previewTime}>
                            {preview ? formatTime(preview.createdAt) : ''}
                          </Text>
                        </View>
                        <Text style={styles.personPreview} numberOfLines={1}>
                          {preview ? preview.content : 'Tap to start chatting'}
                        </Text>
                      </View>
                    </Pressable>
                  );
                })}
              </ScrollView>
            )}
          </View>
        )}

        <View style={styles.bottomNavigation}>
          <Pressable
            style={styles.navItem}
            onPress={() => setActiveTab('chats')}
          >
            <Text
              style={[
                styles.navIcon,
                activeTab === 'chats' && styles.navIconActive,
              ]}
            >
              ◉
            </Text>
            <Text
              style={[
                styles.navLabel,
                activeTab === 'chats' && styles.navLabelActive,
              ]}
            >
              Chats
            </Text>
          </Pressable>
          <Pressable
            style={styles.navItem}
            onPress={() => setActiveTab('contacts')}
          >
            <Text
              style={[
                styles.navIcon,
                activeTab === 'contacts' && styles.navIconActive,
              ]}
            >
              ☷
            </Text>
            <Text
              style={[
                styles.navLabel,
                activeTab === 'contacts' && styles.navLabelActive,
              ]}
            >
              Contacts
            </Text>
          </Pressable>
          <Pressable
            style={styles.navItem}
            onPress={() => setActiveTab('settings')}
          >
            <Text
              style={[
                styles.navIcon,
                activeTab === 'settings' && styles.navIconActive,
              ]}
            >
              ⚙
            </Text>
            <Text
              style={[
                styles.navLabel,
                activeTab === 'settings' && styles.navLabelActive,
              ]}
            >
              Settings
            </Text>
          </Pressable>
        </View>

        {chatLoading && (
          <View style={styles.loadingOverlay}>
            <ActivityIndicator color="#FFFFFF" size="large" />
            <Text style={styles.loadingOverlayText}>Opening conversation...</Text>
          </View>
        )}
      </SafeAreaView>
    );
  }

  // -------------------- LINK EXISTING ACCOUNT --------------------
  if (showLinkDevice && !token) {
    return (
      <SafeAreaView style={styles.authSafeArea}>
        <StatusBar barStyle="light-content" />
        <KeyboardAvoidingView
          style={styles.flex}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        >
          <ScrollView
            contentContainerStyle={styles.authScrollContent}
            keyboardShouldPersistTaps="handled"
          >
            <View style={styles.authBrand}>
              <View style={styles.authLogo}><Text style={styles.authLogoText}>S</Text></View>
              <Text style={styles.authBrandName}>Link this device</Text>
              <Text style={styles.authTagline}>
                Connect to an account that already has a primary device.
              </Text>
            </View>

            <View style={styles.authCard}>
              <Text style={styles.authHeading}>Link existing account</Text>
              <Text style={styles.authDescription}>
                Generate a temporary code, then approve it from the account's primary device.
              </Text>

              {!linkingRequest ? (
                <Pressable
                  style={[styles.authButton, linkingBusy && styles.authButtonDisabled]}
                  onPress={() => void createDeviceLinkRequest()}
                  disabled={linkingBusy}
                >
                  {linkingBusy ? (
                    <ActivityIndicator color="#FFFFFF" />
                  ) : (
                    <Text style={styles.authButtonText}>Generate pairing code</Text>
                  )}
                </Pressable>
              ) : (
                <>
                  <Text style={styles.authLabel}>Your pairing code</Text>
                  <View style={styles.pairingCodeDisplay}>
                    <Text style={styles.pairingCodeText}>{linkingRequest.pairingCode}</Text>
                  </View>
                  <Text style={styles.settingsDetail}>
                    This code expires at {new Date(linkingRequest.expiresAt).toLocaleTimeString()}.
                    Enter it on your primary device under Settings → Device Management.
                  </Text>
                  <View style={styles.linkWaitingRow}>
                    <ActivityIndicator color="#128C7E" />
                    <Text style={styles.linkStatusText}>
                      {linkStatus || 'Waiting for approval...'}
                    </Text>
                  </View>
                </>
              )}

              <Pressable
                style={styles.authSwitch}
                onPress={async () => {
                  setLinkPollToken(null);
                  setLinkingRequest(null);
                  setLinkStatus('');
                  setShowLinkDevice(false);
                  await SecureStore.deleteItemAsync(STORAGE_KEYS.pendingPollToken).catch(() => undefined);
                }}
              >
                <Text style={styles.authSwitchHighlight}>Back to sign in</Text>
              </Pressable>
            </View>

            <Text style={styles.authFooter}>SECURECONNECT · ANDROID</Text>
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    );
  }

  // -------------------- LOGIN / REGISTER --------------------
  return (
    <SafeAreaView style={styles.authSafeArea}>
      <StatusBar barStyle="light-content" />
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.authScrollContent}
          keyboardShouldPersistTaps="handled"
        >
          <View style={styles.authBrand}>
            <View style={styles.authLogo}>
              <Text style={styles.authLogoText}>S</Text>
            </View>
            <Text style={styles.authBrandName}>SecureConnect</Text>
            <Text style={styles.authTagline}>Your conversations, connected.</Text>
          </View>

          <View style={styles.authCard}>
            <Text style={styles.authHeading}>
              {isRegister ? 'Create account' : 'Welcome back'}
            </Text>
            <Text style={styles.authDescription}>
              {isRegister
                ? 'Join SecureConnect and start connecting.'
                : 'Sign in to continue to your conversations.'}
            </Text>

            {isRegister && (
              <>
                <Text style={styles.authLabel}>Username</Text>
                <TextInput
                  style={styles.authInput}
                  placeholder="Choose a username"
                  placeholderTextColor="#8B969B"
                  value={username}
                  onChangeText={setUsername}
                  autoCapitalize="none"
                  autoCorrect={false}
                  editable={!loading}
                  returnKeyType="next"
                />
              </>
            )}

            <Text style={styles.authLabel}>Email address</Text>
            <TextInput
              style={styles.authInput}
              placeholder="you@example.com"
              placeholderTextColor="#8B969B"
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
              editable={!loading}
              returnKeyType="next"
            />

            <Text style={styles.authLabel}>Password</Text>
            <TextInput
              style={styles.authInput}
              placeholder="Enter your password"
              placeholderTextColor="#8B969B"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              editable={!loading}
              returnKeyType="done"
              onSubmitEditing={() => {
                if (!loading) void handleContinue();
              }}
            />

            <Pressable
              disabled={loading}
              style={({ pressed }) => [
                styles.authButton,
                pressed && styles.authButtonPressed,
                loading && styles.authButtonDisabled,
              ]}
              onPress={() => void handleContinue()}
            >
              {loading ? (
                <View style={styles.authLoadingRow}>
                  <ActivityIndicator color="#FFFFFF" size="small" />
                  <Text style={styles.authButtonText}>Please wait...</Text>
                </View>
              ) : (
                <Text style={styles.authButtonText}>
                  {isRegister ? 'Create Account' : 'Sign In'}
                </Text>
              )}
            </Pressable>

            <Pressable
              disabled={loading}
              style={styles.authSwitch}
              onPress={() => {
                setIsRegister(!isRegister);
                setPassword('');
              }}
            >
              <Text style={styles.authSwitchText}>
                {isRegister ? 'Already have an account? ' : "Don't have an account? "}
                <Text style={styles.authSwitchHighlight}>
                  {isRegister ? 'Sign in' : 'Register'}
                </Text>
              </Text>
            </Pressable>

            {!isRegister && (
              <Pressable
                style={styles.linkDeviceAuthButton}
                onPress={() => {
                  setShowLinkDevice(true);
                  setLinkingRequest(null);
                  setLinkStatus('');
                }}
              >
                <Text style={styles.linkDeviceAuthText}>
                  Link an existing account on this device
                </Text>
              </Pressable>
            )}
          </View>

          <Text style={styles.authFooter}>SECURECONNECT · ANDROID</Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  safeArea: { flex: 1, backgroundColor: '#F5F7F6' },
  authSafeArea: { flex: 1, backgroundColor: '#075E54' },
  restoreLoading: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  restoreLoadingText: { color: '#FFFFFF', fontSize: 13, marginTop: 12 },
  linkDeviceAuthButton: { alignItems: 'center', paddingTop: 18 },
  linkDeviceAuthText: { color: '#087568', fontSize: 13, fontWeight: '800' },
  pairingCodeDisplay: {
    minHeight: 64,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#E4F2EE',
    borderRadius: 12,
    marginBottom: 12,
    padding: 12,
  },
  pairingCodeText: { color: '#075E54', fontSize: 27, fontWeight: '900', letterSpacing: 4 },
  linkWaitingRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', marginTop: 18 },
  linkStatusText: { color: '#53645D', fontSize: 12, marginLeft: 9, flexShrink: 1 },

  // Home header and chat list
  homeHeader: {
    minHeight: 104,
    backgroundColor: '#075E54',
    paddingHorizontal: 20,
    paddingTop: 15,
    paddingBottom: 15,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  homeBrand: { color: '#FFFFFF', fontSize: 24, fontWeight: '800' },
  homeSubtitle: { color: '#D4E9E5', fontSize: 12, marginTop: 5 },
  newChatButton: {
    width: 43,
    height: 43,
    borderRadius: 22,
    backgroundColor: '#128C7E',
    alignItems: 'center',
    justifyContent: 'center',
  },
  newChatButtonText: { color: '#FFFFFF', fontSize: 29, lineHeight: 33 },
  searchWrap: {
    height: 46,
    marginHorizontal: 15,
    marginTop: 12,
    marginBottom: 5,
    paddingHorizontal: 13,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#E5EAE8',
  },
  searchIcon: { color: '#65736F', fontSize: 23, marginRight: 8 },
  searchInput: { flex: 1, color: '#1F2D2A', fontSize: 14, paddingVertical: 7 },
  clearSearch: { color: '#65736F', fontSize: 24, paddingHorizontal: 4 },
  homeContent: { flex: 1, paddingTop: 14 },
  listHeadingRow: {
    paddingHorizontal: 19,
    paddingBottom: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  screenTitle: { color: '#172B26', fontSize: 21, fontWeight: '800' },
  listSubtitle: { color: '#71807B', fontSize: 12, marginTop: 4 },
  refreshSmallButton: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: '#E4F2EE',
  },
  refreshSmallButtonText: { color: '#087568', fontSize: 12, fontWeight: '700' },
  userList: { paddingBottom: 15 },
  userRow: {
    minHeight: 76,
    paddingHorizontal: 17,
    paddingVertical: 10,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E1E7E4',
  },
  userRowPressed: { backgroundColor: '#EDF5F2' },
  personAvatar: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: '#D5EEE7',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 13,
  },
  personAvatarText: { color: '#075E54', fontSize: 21, fontWeight: '700' },
  personTextBlock: { flex: 1, justifyContent: 'center' },
  personNameLine: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  personName: { color: '#172B26', fontSize: 15, fontWeight: '700' },
  previewTime: { color: '#8B9691', fontSize: 10, marginLeft: 8 },
  personPreview: { color: '#73817C', fontSize: 12, marginTop: 5 },
  homeLoading: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyPeople: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 30,
  },
  emptyPeopleTitle: { color: '#263B35', fontSize: 17, fontWeight: '700' },
  emptyMessageText: {
    color: '#788780',
    fontSize: 13,
    textAlign: 'center',
    marginTop: 9,
    lineHeight: 19,
  },

  // Bottom navigation
  bottomNavigation: {
    height: 62,
    backgroundColor: '#FFFFFF',
    borderTopWidth: 1,
    borderTopColor: '#E3EAE6',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
  },
  navItem: {
    flex: 1,
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  navIcon: { color: '#87938E', fontSize: 20 },
  navIconActive: { color: '#087568' },
  navLabel: { color: '#87938E', fontSize: 10, marginTop: 3 },
  navLabelActive: { color: '#087568', fontWeight: '800' },

  // Conversation screen
  chatHeader: {
    minHeight: 62,
    backgroundColor: '#075E54',
    paddingHorizontal: 10,
    flexDirection: 'row',
    alignItems: 'center',
  },
  backButton: { paddingHorizontal: 7, paddingVertical: 4 },
  backButtonText: { color: '#FFFFFF', fontSize: 34, lineHeight: 36 },
  smallAvatar: {
    width: 39,
    height: 39,
    borderRadius: 20,
    backgroundColor: '#D5EEE7',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  avatarInitial: { color: '#075E54', fontSize: 16, fontWeight: '800' },
  chatHeaderInfo: { flex: 1 },
  chatTitle: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  chatSubtitle: { color: '#D4E9E5', fontSize: 11, marginTop: 2 },
  headerAction: { paddingHorizontal: 12, paddingVertical: 7 },
  headerActionText: { color: '#FFFFFF', fontSize: 25 },
  messagesArea: { flex: 1, backgroundColor: '#EDE8E1' },
  messagesContent: {
    paddingHorizontal: 12,
    paddingTop: 15,
    paddingBottom: 15,
    flexGrow: 1,
    justifyContent: 'flex-end',
  },
  chatLoading: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyChatCard: {
    alignSelf: 'center',
    maxWidth: '88%',
    paddingHorizontal: 18,
    paddingVertical: 15,
    backgroundColor: '#FFF8D8',
    borderRadius: 12,
    marginVertical: 20,
  },
  emptyChatTitle: {
    color: '#5B5745',
    textAlign: 'center',
    fontSize: 15,
    fontWeight: '700',
  },
  emptyChatText: {
    color: '#6E6855',
    textAlign: 'center',
    fontSize: 12,
    lineHeight: 18,
    marginTop: 5,
  },
  messageBubble: {
    maxWidth: '84%',
    paddingHorizontal: 11,
    paddingTop: 8,
    paddingBottom: 6,
    borderRadius: 11,
    marginBottom: 8,
  },
  myMessage: {
    alignSelf: 'flex-end',
    backgroundColor: '#D9FDD3',
    borderTopRightRadius: 3,
  },
  theirMessage: {
    alignSelf: 'flex-start',
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 3,
  },
  messageSender: {
    color: '#087568',
    fontSize: 11,
    fontWeight: '700',
    marginBottom: 3,
  },
  messageText: { color: '#202C28', fontSize: 14, lineHeight: 20 },
  messageTime: {
    color: '#7C8983',
    fontSize: 9,
    textAlign: 'right',
    marginTop: 4,
  },
  composer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    paddingHorizontal: 9,
    paddingVertical: 8,
    backgroundColor: '#F4F6F5',
  },
  messageInput: {
    flex: 1,
    minHeight: 43,
    maxHeight: 105,
    backgroundColor: '#FFFFFF',
    borderRadius: 22,
    borderWidth: 1,
    borderColor: '#E1E8E4',
    paddingHorizontal: 16,
    paddingVertical: 10,
    color: '#1F2D2A',
    fontSize: 14,
    marginRight: 8,
  },
  sendButton: {
    width: 43,
    height: 43,
    borderRadius: 22,
    backgroundColor: '#128C7E',
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendButtonDisabled: { opacity: 0.55 },
  sendButtonText: { color: '#FFFFFF', fontSize: 19, fontWeight: '700' },
  encryptionNotice: {
    color: '#7C8983',
    fontSize: 9,
    textAlign: 'center',
    backgroundColor: '#F4F6F5',
    paddingBottom: 5,
  },

  // Settings
  settingsContent: { padding: 18, paddingBottom: 25 },
  profileCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 15,
    padding: 16,
    marginTop: 17,
    borderWidth: 1,
    borderColor: '#E5EBE8',
  },
  profileAvatar: {
    width: 58,
    height: 58,
    borderRadius: 29,
    backgroundColor: '#D5EEE7',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 14,
  },
  profileAvatarText: { color: '#075E54', fontSize: 24, fontWeight: '800' },
  profileInfo: { flex: 1 },
  profileName: { color: '#172B26', fontSize: 17, fontWeight: '700' },
  profileEmail: { color: '#788780', fontSize: 12, marginTop: 5 },
  settingsCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 15,
    padding: 17,
    marginTop: 15,
    borderWidth: 1,
    borderColor: '#E5EBE8',
  },
  settingsLabel: {
    color: '#087568',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1,
    marginBottom: 8,
  },
  settingsItem: { color: '#263B35', fontSize: 14, fontWeight: '700' },
  settingsDetail: { color: '#788780', fontSize: 12, lineHeight: 18, marginTop: 5 },
  settingsDivider: {
    height: 1,
    backgroundColor: '#E9EEEB',
    marginVertical: 16,
  },
  deviceCodeInput: {
    minHeight: 48,
    backgroundColor: '#F7F9F8',
    borderWidth: 1,
    borderColor: '#DCE5E0',
    borderRadius: 11,
    paddingHorizontal: 13,
    color: '#172B26',
    fontSize: 15,
    fontWeight: '700',
    letterSpacing: 1.5,
    marginTop: 13,
    marginBottom: 10,
  },
  deviceActionButton: {
    minHeight: 45,
    backgroundColor: '#128C7E',
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 12,
  },
  deviceActionDisabled: { opacity: 0.6 },
  deviceActionButtonText: { color: '#FFFFFF', fontSize: 13, fontWeight: '800' },
  deviceListHeading: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  deviceRow: {
    minHeight: 58,
    flexDirection: 'row',
    alignItems: 'center',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#E5EBE8',
    paddingVertical: 9,
  },
  deviceRowInfo: { flex: 1, paddingRight: 10 },
  deviceRowName: { color: '#263B35', fontSize: 13, fontWeight: '700' },
  deviceRowMeta: { color: '#788780', fontSize: 11, marginTop: 4 },
  revokeButton: {
    borderWidth: 1,
    borderColor: '#E7B8B5',
    borderRadius: 9,
    paddingHorizontal: 12,
    paddingVertical: 7,
  },
  revokeButtonText: { color: '#B13B36', fontSize: 11, fontWeight: '800' },
  signOutButton: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#D9E5E0',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 18,
  },
  signOutButtonText: { color: '#B13B36', fontSize: 14, fontWeight: '700' },
  versionText: {
    color: '#89958F',
    fontSize: 10,
    letterSpacing: 1.5,
    textAlign: 'center',
    marginTop: 22,
  },
  loadingOverlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(7, 38, 33, 0.72)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingOverlayText: { color: '#FFFFFF', fontSize: 13, marginTop: 10 },

  // Authentication
  authScrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
    paddingVertical: 35,
    backgroundColor: '#075E54',
  },
  authBrand: { alignItems: 'center', marginBottom: 28 },
  authLogo: {
    width: 66,
    height: 66,
    borderRadius: 22,
    backgroundColor: '#128C7E',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 13,
  },
  authLogoText: { color: '#FFFFFF', fontSize: 36, fontWeight: '800' },
  authBrandName: { color: '#FFFFFF', fontSize: 29, fontWeight: '800' },
  authTagline: { color: '#D4E9E5', fontSize: 13, marginTop: 7 },
  authCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 22,
    padding: 23,
    width: '100%',
  },
  authHeading: { color: '#172B26', fontSize: 25, fontWeight: '800' },
  authDescription: {
    color: '#788780',
    fontSize: 13,
    lineHeight: 20,
    marginTop: 8,
    marginBottom: 22,
  },
  authLabel: {
    color: '#344740',
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 7,
  },
  authInput: {
    minHeight: 50,
    backgroundColor: '#F7F9F8',
    borderWidth: 1,
    borderColor: '#DCE5E0',
    borderRadius: 11,
    paddingHorizontal: 14,
    color: '#172B26',
    fontSize: 14,
    marginBottom: 17,
  },
  authButton: {
    minHeight: 51,
    backgroundColor: '#128C7E',
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 12,
    marginTop: 5,
  },
  authButtonPressed: { opacity: 0.82 },
  authButtonDisabled: { opacity: 0.6 },
  authButtonText: { color: '#FFFFFF', fontSize: 15, fontWeight: '800' },
  authLoadingRow: { flexDirection: 'row', alignItems: 'center' },
  authSwitch: { paddingTop: 21, alignItems: 'center' },
  authSwitchText: { color: '#788780', fontSize: 12, textAlign: 'center' },
  authSwitchHighlight: { color: '#087568', fontWeight: '800' },
  authFooter: {
    color: '#D4E9E5',
    fontSize: 10,
    textAlign: 'center',
    letterSpacing: 1.5,
    marginTop: 25,
  },
});
