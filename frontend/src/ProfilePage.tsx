import { useEffect, useState } from "react";
import {
  ArrowLeft,
  ArrowUpRight,
  MessageSquare,
  Package,
  ShieldCheck,
  Star,
} from "lucide-react";
import type {
  Account,
  Advice,
  Marketplace,
  PublicProfile,
  Review,
} from "./types";
import { Empty } from "./components";
import Username from "./Username";
import { AdviceArtwork, Avatar } from "./Media";
import { validProfileFields } from "./api";
import { money } from "./storage";
import SellerRating from "./SellerRating";

export default function ProfilePage({
  username,
  client,
  account,
  onBack,
  onOpenAdvice,
}: {
  username: string;
  client: Marketplace;
  account: Account | null;
  onBack: () => void;
  onOpenAdvice: (advice: Advice) => void;
}) {
  const [profile, setProfile] = useState<PublicProfile | null>(null);
  const [advices, setAdvices] = useState<Advice[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [tab, setTab] = useState<"advices" | "reviews">("advices");
  const [errors, setErrors] = useState({
    profile: "",
    advices: "",
    reviews: "",
  });
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setErrors({ profile: "", advices: "", reviews: "" });
    setProfile(null);
    setAdvices([]);
    setReviews([]);
    void Promise.allSettled([
      client.userProfile(username),
      client.userAdvices(username),
      client.userReviews(username),
    ]).then(([person, listings, feedback]) => {
      if (!active) return;
      const next = { profile: "", advices: "", reviews: "" };
      if (person.status === "fulfilled") {
        const value = person.value;
        if (
          value &&
          value.username === username &&
          (value.userRole === "USER" || value.userRole === "ADMIN") &&
          validProfileFields(value)
        )
          setProfile(value);
        else next.profile = "Сервер вернул некорректный профиль.";
      } else
        next.profile =
          person.reason instanceof Error
            ? person.reason.message
            : "Не удалось загрузить профиль.";
      if (listings.status === "fulfilled") setAdvices(listings.value);
      else
        next.advices =
          listings.reason instanceof Error
            ? listings.reason.message
            : "Не удалось загрузить объявления.";
      if (feedback.status === "fulfilled") setReviews(feedback.value);
      else
        next.reviews =
          feedback.reason instanceof Error
            ? feedback.reason.message
            : "Не удалось загрузить отзывы.";
      setErrors(next);
      setLoading(false);
    });
    return () => {
      active = false;
    };
  }, [client, username, retry]);
  const retryButton = (
    <button
      className="button button-secondary"
      onClick={() => setRetry((value) => value + 1)}
    >
      Попробовать снова
    </button>
  );
  if (loading)
    return (
      <section className="content-page" role="status">
        Загружаем профиль…
      </section>
    );
  if (!profile || errors.profile)
    return (
      <section className="content-page">
        <button className="text-button back-link" onClick={onBack}>
          <ArrowLeft size={17} />В маркет
        </button>
        <Empty
          title="Не удалось открыть профиль"
          description={errors.profile}
          action={retryButton}
        />
      </section>
    );
  const average = reviews.length
    ? (
        reviews.reduce((sum, review) => sum + review.rating, 0) / reviews.length
      ).toFixed(1)
    : "—";
  return (
    <section
      className="content-page profile-page"
      aria-label={`Профиль ${username}`}
    >
      <button className="text-button back-link" onClick={onBack}>
        <ArrowLeft size={17} />В маркет
      </button>
      <div className="public-profile-heading">
        <Avatar user={profile} className="public-profile-avatar" />
        <div>
          <h1>
            <Username user={profile} interactive={false} />
          </h1>
          {profile.userRole === "ADMIN" ? (
            <span className="profile-role">
              <ShieldCheck size={15} />
              Администратор
            </span>
          ) : null}
        </div>
        <div className="profile-stat">
          <Star size={20} />
          <strong>{errors.reviews ? "—" : average}</strong>
          <small>Оценка по отзывам</small>
        </div>
      </div>
      {(profile.age != null || profile.city) && (
        <p className="profile-details">
          {[
            profile.age != null
              ? `${profile.age} ${new Intl.PluralRules("ru").select(profile.age) === "one" ? "год" : new Intl.PluralRules("ru").select(profile.age) === "few" ? "года" : "лет"}`
              : null,
            profile.city,
          ]
            .filter(Boolean)
            .join(" · ")}
        </p>
      )}
      {profile.about && <p className="profile-about">{profile.about}</p>}
      <div className="profile-tabs" role="group" aria-label="Разделы профиля">
        <button
          className={tab === "advices" ? "selected" : ""}
          aria-pressed={tab === "advices"}
          onClick={() => setTab("advices")}
        >
          <Package size={16} />
          Активные объявления {!errors.advices && <span>{advices.length}</span>}
        </button>
        <button
          className={tab === "reviews" ? "selected" : ""}
          aria-pressed={tab === "reviews"}
          onClick={() => setTab("reviews")}
        >
          <MessageSquare size={16} />
          Отзывы {!errors.reviews && <span>{reviews.length}</span>}
        </button>
      </div>
      {tab === "advices" ? (
        errors.advices ? (
          <Empty
            title="Не удалось загрузить объявления"
            description={errors.advices}
            action={retryButton}
          />
        ) : !advices.length ? (
          <Empty title="Нет активных объявлений" />
        ) : (
          <div className="listing-grid">
            {advices.map((advice) => (
              <article className="listing-card" key={advice.id}>
                <button
                  className="listing-open-art"
                  aria-label={`Открыть объявление ${advice.name}`}
                  onClick={() => onOpenAdvice(advice)}
                >
                  <AdviceArtwork advice={advice} />
                </button>
                <div className="listing-info">
                  <span className="listing-subtitle">{advice.game.name}</span>
                  <h3>
                    <button
                      className="listing-open-title"
                      onClick={() => onOpenAdvice(advice)}
                    >
                      {advice.name}
                    </button>
                  </h3>
                  <div className="seller">
                    <span className="seller-identity">
                      <Username user={advice.userSeller} account={account} />
                      <SellerRating
                        client={client}
                        username={advice.userSeller.username}
                        reviews={reviews}
                      />
                    </span>
                  </div>
                  <div className="listing-bottom">
                    <strong className="listing-price">
                      {money(advice.price)} ₽
                    </strong>
                    <button
                      className="buy-button"
                      onClick={() => onOpenAdvice(advice)}
                    >
                      Подробнее <ArrowUpRight size={15} />
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )
      ) : errors.reviews ? (
        <Empty
          title="Не удалось загрузить отзывы"
          description={errors.reviews}
          action={retryButton}
        />
      ) : !reviews.length ? (
        <Empty title="Отзывов пока нет" />
      ) : (
        <div className="profile-reviews">
          {reviews.map((review, index) => (
            <article className="profile-review" key={review.orderId ?? index}>
              <header>
                {review.userBuyer ? (
                  <Username user={review.userBuyer} account={account} />
                ) : (
                  <span>Покупатель</span>
                )}
                <span
                  className="profile-review-stars"
                  aria-label={`Оценка ${review.rating} из 5`}
                >
                  {[1, 2, 3, 4, 5].map((value) => (
                    <Star
                      key={value}
                      size={15}
                      fill={value <= review.rating ? "currentColor" : "none"}
                    />
                  ))}
                </span>
              </header>
              {review.adviceName && <small>{review.adviceName}</small>}
              <p>{review.feedbackText}</p>
              {review.sellerResponseText && (
                <blockquote>
                  <small>
                    Ответ продавца ·{" "}
                    <Username user={profile} interactive={false} />
                  </small>
                  <p>{review.sellerResponseText}</p>
                </blockquote>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
