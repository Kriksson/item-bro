export type Game = {
  id: number;
  name: string;
  theme?: number;
  imageUrl?: string | null;
};
export type PublicUser = {
  username: string;
  userRole?: UserRole;
  avatarUrl?: string | null;
};
export type ProfileFields = {
  age: number | null;
  city: string | null;
  about: string | null;
};
export type PublicProfile = PublicUser &
  Partial<ProfileFields> & { userRole: UserRole };
export type Review = {
  orderId?: number;
  adviceName?: string;
  userBuyer?: PublicUser;
  rating: number;
  feedbackText: string;
  sellerResponseText: string | null;
};
export type Advice = {
  id: number;
  name: string;
  game: Game;
  userSeller: PublicUser;
  price: number;
  imageUrl?: string | null;
};
export type AdvicePage = {
  content: Advice[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
export type Order = {
  id: number;
  adviceDTO: Advice;
  userBuyerDTO: PublicUser;
  price: number;
  rating: number | null;
  feedbackText: string | null;
  sellerResponseText: string | null;
  orderStatus?: "OPENED" | "CLOSED" | "REFUND" | "ARCHIVED";
};
export type UserRole = "USER" | "ADMIN";
export type UserProfile = Partial<ProfileFields> & {
  name: string;
  userRole: UserRole;
  balance: number;
  avatarUrl?: string | null;
};
export type Account = Partial<ProfileFields> & {
  name: string;
  balance?: number;
  role: UserRole;
  avatarUrl?: string | null;
};
export type ChatMessage = {
  id: number;
  orderId: number;
  sender: PublicUser;
  text: string;
  sentAt: string;
};
export type MessagePage = { items: ChatMessage[]; hasMore: boolean };
export type Page =
  "market" | "orders" | "selling" | "admin" | "order" | "profile";
export interface Marketplace {
  login(name: string, password: string): Promise<Account>;
  register(name: string, password: string): Promise<Account>;
  restoreSession(): Promise<Account | null>;
  logout(): Promise<void>;
  games(): Promise<Game[]>;
  orders(): Promise<Order[]>;
  advices(gameId: number, page: number): Promise<AdvicePage>;
  createAdvice(name: string, gameId: number, price: number): Promise<Advice>;
  archiveAdvice(id: number): Promise<void>;
  createOrder(adviceId: number): Promise<Order>;
  order(id: number): Promise<Order>;
  action(id: number, action: "confirm" | "cancel"): Promise<void>;
  feedback(id: number, rating: number, feedbackText: string): Promise<void>;
  response(id: number, responseText: string): Promise<void>;
  createGame(name: string): Promise<Game>;
  archiveGame(id: number): Promise<void>;
  userProfile(username: string): Promise<PublicProfile>;
  userAdvices(username: string): Promise<Advice[]>;
  userReviews(username: string): Promise<Review[]>;
  adminOrders(): Promise<Order[]>;
  updateProfile(fields: ProfileFields): Promise<Account>;
  uploadAvatar(file: File): Promise<Account>;
  removeAvatar(): Promise<Account>;
  uploadGameImage(id: number, file: File): Promise<Game>;
  removeGameImage(id: number): Promise<Game>;
  uploadAdviceImage(id: number, file: File): Promise<Advice>;
  removeAdviceImage(id: number): Promise<Advice>;
  messages(
    id: number,
    cursor?: { beforeId?: number; afterId?: number },
  ): Promise<MessagePage>;
  sendMessage(
    id: number,
    text: string,
    clientMessageId: string,
  ): Promise<ChatMessage>;
}
