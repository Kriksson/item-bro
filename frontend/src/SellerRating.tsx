import { useEffect, useState } from "react";
import { Star } from "lucide-react";
import type { Marketplace, Review } from "./types";

const pending = new WeakMap<Marketplace, Map<string, Promise<number | null>>>();
const format = new Intl.NumberFormat("ru-RU", {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

function average(reviews: Review[]): number | null {
  if (!reviews.length) return null;
  return (
    reviews.reduce((sum, review) => sum + review.rating, 0) / reviews.length
  );
}

function loadRating(client: Marketplace, username: string) {
  let requests = pending.get(client);
  if (!requests) {
    requests = new Map();
    pending.set(client, requests);
  }
  const existing = requests.get(username);
  if (existing) return existing;
  const request = client
    .userReviews(username)
    .then(average)
    .finally(() => requests.delete(username));
  requests.set(username, request);
  return request;
}

export default function SellerRating({
  client,
  username,
  reviews,
}: {
  client: Marketplace;
  username: string;
  reviews?: Review[];
}) {
  const [loaded, setLoaded] = useState<{
    username: string;
    value: number | null;
  } | null>(null);

  useEffect(() => {
    if (reviews !== undefined) return;
    let active = true;
    void loadRating(client, username).then(
      (value) => {
        if (active) setLoaded({ username, value });
      },
      () => {
        if (active) setLoaded({ username, value: null });
      },
    );
    return () => {
      active = false;
    };
  }, [client, username, reviews]);

  const value =
    reviews !== undefined
      ? average(reviews)
      : loaded?.username === username
        ? loaded.value
        : null;
  if (value == null) return null;
  const text = format.format(value);
  const label = `Средняя оценка продавца ${username}: ${text} из 5`;
  return (
    <span className="seller-rating" role="img" aria-label={label} title={label}>
      <Star size={13} fill="currentColor" aria-hidden="true" />
      <span>{text}</span>
    </span>
  );
}
