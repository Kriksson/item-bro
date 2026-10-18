import { useState } from "react";
import {
  ArrowDownUp,
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Gamepad2,
  Heart,
  Plus,
  Search,
  Trash2,
  X,
} from "lucide-react";
import type { Account, Advice, AdvicePage, Game, Marketplace } from "./types";
import { Empty, HeroArt } from "./components";
import { money } from "./storage";
import Username from "./Username";
import { AdviceArtwork, Avatar, Picture } from "./Media";
import SellerRating from "./SellerRating";

type Props = {
  kind: "market" | "selling";
  account: Account | null;
  client: Marketplace;
  games: Game[];
  gamesLoading: boolean;
  gamesError: string;
  gameId: number | null;
  data: AdvicePage;
  loading: boolean;
  error: string;
  pageNumber: number;
  onChooseGame: (id: number) => void;
  onBack: () => void;
  onRetry: () => void;
  onPage: (page: number) => void;
  onCreate: () => void;
  onBuy: (advice: Advice) => void;
  onOpen: (advice: Advice) => void;
  onArchive: (advice: Advice) => void;
};

export default function MarketPage(props: Props) {
  const {
    kind,
    account,
    client,
    games,
    gamesLoading,
    gamesError,
    gameId,
    data,
    loading,
    error,
    pageNumber,
    onChooseGame,
    onBack,
    onRetry,
    onPage,
    onCreate,
    onBuy,
    onOpen,
    onArchive,
  } = props;
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("new");
  const [favorites, setFavorites] = useState<number[]>([]);
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const selectedGame = games.find((game) => game.id === gameId);
  const isSelling = kind === "selling";
  const CatalogHeading = isSelling ? "h1" : "h2";

  if (isSelling && !account)
    return (
      <section className="content-page">
        <Empty
          title="Войди, чтобы посмотреть объявления"
          action={
            <button className="button" onClick={onCreate}>
              Войти в аккаунт <ArrowRight size={16} />
            </button>
          }
        />
      </section>
    );

  if (gameId === null) {
    const visibleGames = games.filter((game) =>
      game.name.toLowerCase().includes(query.toLowerCase()),
    );
    return (
      <>
        {!isSelling && (
          <section className="hero" aria-label="Маркет">
            <div className="hero-copy">
              <h1>
                Цифровые
                <br />
                <span>товары</span>
              </h1>
              <button className="button" onClick={onCreate}>
                <Plus size={17} />
                Разместить объявление
              </button>
            </div>
            <HeroArt />
          </section>
        )}
        <section
          id="game-catalog"
          className="catalog-section"
          aria-label="Каталог игр и сервисов"
        >
          <div className="section-heading">
            <div>
              <CatalogHeading>
                {isSelling ? "Мои объявления" : "Игры и сервисы"}
                <span className="heading-dot">.</span>
              </CatalogHeading>
            </div>
            {isSelling && (
              <button
                className="button button-secondary button-small"
                onClick={onCreate}
              >
                <Plus size={16} />
                Разместить объявление
              </button>
            )}
          </div>
          <div className="games-toolbar">
            <label className="search-field">
              <Search size={17} />
              <input
                aria-label="Поиск по каталогу"
                placeholder="Найти игру или сервис"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
              {query && (
                <button
                  aria-label="Очистить поиск по каталогу"
                  className="clear-search"
                  onClick={() => setQuery("")}
                >
                  <X size={14} />
                </button>
              )}
            </label>
          </div>
          {gamesLoading ? (
            <div className="games-grid" aria-label="Загрузка категорий">
              {[0, 1, 2].map((i) => (
                <div key={i} className="skeleton-card" />
              ))}
            </div>
          ) : gamesError ? (
            <Empty
              title="Не удалось загрузить каталог"
              description={gamesError}
              action={
                <button className="button button-secondary" onClick={onRetry}>
                  Попробовать снова
                </button>
              }
            />
          ) : !visibleGames.length ? (
            <Empty title={query ? "Категория не найдена" : "Каталог пуст"} />
          ) : (
            <div className="games-grid">
              {visibleGames.map((game) => (
                <button
                  className={`game-card game-cover-theme-${game.id % 4}`}
                  key={game.id}
                  aria-label={`Открыть категорию ${game.name}`}
                  onClick={() => onChooseGame(game.id)}
                >
                  <Picture
                    src={game.imageUrl}
                    alt={game.name}
                    className="game-cover-image"
                    fallback={
                      <>
                        <div className="game-cover" aria-hidden="true">
                          <div className="game-cover-grid" />
                          <span className="game-cover-orbit" />
                          <Gamepad2
                            className="game-cover-controller"
                            strokeWidth={0.9}
                          />
                          <span className="game-cover-letter">
                            {game.name.slice(0, 2).toUpperCase()}
                          </span>
                        </div>
                      </>
                    }
                  />
                  <div className="game-card-bottom">
                    <span>
                      <strong>{game.name}</strong>
                    </span>
                    <span className="game-card-arrow">
                      <ArrowUpRight size={19} />
                    </span>
                  </div>
                </button>
              ))}
            </div>
          )}
        </section>
      </>
    );
  }

  const visible = data.content.filter(
    (advice) =>
      (!isSelling || advice.userSeller.username === account?.name) &&
      (!favoritesOnly || favorites.includes(advice.id)) &&
      `${advice.name} ${advice.userSeller.username}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  visible.sort((a, b) =>
    sort === "low"
      ? a.price - b.price
      : sort === "high"
        ? b.price - a.price
        : b.id - a.id,
  );
  return (
    <section
      className="catalog-section game-listings"
      aria-label="Каталог объявлений"
    >
      <button className="text-button back-link" onClick={onBack}>
        <ArrowLeft size={17} />В каталог
      </button>
      <div className="section-heading">
        <div>
          <h1>
            {selectedGame?.name ?? "Объявления"}
            <span className="heading-dot">.</span>
          </h1>
        </div>
        <button
          className="button button-secondary button-small"
          onClick={onCreate}
        >
          <Plus size={16} />
          Разместить объявление
        </button>
      </div>
      <div className="catalog-toolbar">
        <div className="catalog-title">
          {isSelling ? "Мои объявления" : "Объявления"}
          <span className="count-badge">
            {isSelling || query || favoritesOnly
              ? visible.length
              : data.totalElements}
          </span>
        </div>
        <div className="catalog-controls">
          <label className="search-field">
            <Search size={17} />
            <input
              aria-label="Поиск предмета на странице"
              placeholder="Найти предмет на странице"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            {query && (
              <button
                className="clear-search"
                aria-label="Очистить поиск"
                onClick={() => setQuery("")}
              >
                <X size={14} />
              </button>
            )}
          </label>
          <button
            className={`favorites-filter ${favoritesOnly ? "selected" : ""}`}
            aria-label="Избранное"
            aria-pressed={favoritesOnly}
            onClick={() => setFavoritesOnly((value) => !value)}
          >
            <Heart size={16} fill={favoritesOnly ? "currentColor" : "none"} />
          </button>
          <label className="sort-field">
            <ArrowDownUp size={15} />
            <select
              aria-label="Сортировка объявлений на странице"
              value={sort}
              onChange={(e) => setSort(e.target.value)}
            >
              <option value="new">Сначала новые</option>
              <option value="low">Сначала дешевле</option>
              <option value="high">Сначала дороже</option>
            </select>
          </label>
        </div>
      </div>
      {loading ? (
        <div className="listing-grid" aria-label="Загрузка объявлений">
          {[0, 1, 2].map((i) => (
            <div className="skeleton-card" key={i} />
          ))}
        </div>
      ) : error ? (
        <Empty
          title="Не удалось загрузить объявления"
          description={error}
          action={
            <button className="button button-secondary" onClick={onRetry}>
              Попробовать снова
            </button>
          }
        />
      ) : !visible.length ? (
        <Empty
          title={
            query || favoritesOnly ? "Ничего не найдено" : "Объявлений нет"
          }
          action={
            <button className="button button-secondary" onClick={onCreate}>
              <Plus size={16} />
              Разместить объявление
            </button>
          }
        />
      ) : (
        <div className="listing-grid">
          {visible.map((advice, index) => (
            <article className="listing-card" key={advice.id}>
              <div className="listing-art-wrap">
                <button
                  className="listing-open-art"
                  aria-label={`Открыть объявление ${advice.name}`}
                  onClick={() => onOpen(advice)}
                >
                  <AdviceArtwork advice={advice} />
                </button>
                <span className="item-category">{advice.game.name}</span>
                <button
                  className={`favorite-button ${favorites.includes(advice.id) ? "is-favorite" : ""}`}
                  aria-label={`${favorites.includes(advice.id) ? "Убрать из избранного" : "В избранное"}: ${advice.name}`}
                  aria-pressed={favorites.includes(advice.id)}
                  onClick={() =>
                    setFavorites((previous) =>
                      previous.includes(advice.id)
                        ? previous.filter((id) => id !== advice.id)
                        : [...previous, advice.id],
                    )
                  }
                >
                  <Heart
                    size={17}
                    fill={
                      favorites.includes(advice.id) ? "currentColor" : "none"
                    }
                  />
                </button>
              </div>
              <div className="listing-info">
                <h3 title={advice.name}>
                  <button
                    className="listing-open-title"
                    onClick={() => onOpen(advice)}
                  >
                    {advice.name}
                  </button>
                </h3>
                <div className="seller">
                  <Avatar
                    user={advice.userSeller}
                    className={`seller-avatar seller-color-${index % 3}`}
                  />
                  <span className="seller-identity">
                    <Username user={advice.userSeller} account={account} />
                    <SellerRating
                      client={client}
                      username={advice.userSeller.username}
                    />
                  </span>
                  {advice.userSeller.username === account?.name && (
                    <span className="own-label">это ты</span>
                  )}
                </div>
                <div className="listing-bottom">
                  <div className="listing-price">
                    {money(advice.price)} <span>₽</span>
                  </div>
                  {isSelling ? (
                    <button
                      className="button button-secondary button-small"
                      onClick={() => onArchive(advice)}
                    >
                      <Trash2 size={14} />В архив
                    </button>
                  ) : (
                    <button
                      className="buy-button"
                      disabled={advice.userSeller.username === account?.name}
                      onClick={() => onBuy(advice)}
                    >
                      {advice.userSeller.username === account?.name
                        ? "Твой предмет"
                        : "Купить"}
                      <ArrowUpRight size={15} />
                    </button>
                  )}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
      {data.totalPages > 1 && (
        <div className="pagination">
          <span>
            Страница {pageNumber + 1} из {data.totalPages}
          </span>
          <button
            className="icon-button"
            aria-label="Предыдущая страница"
            disabled={pageNumber === 0 || loading}
            onClick={() => onPage(pageNumber - 1)}
          >
            <ChevronLeft size={18} />
          </button>
          <button
            className="icon-button"
            aria-label="Следующая страница"
            disabled={pageNumber + 1 >= data.totalPages || loading}
            onClick={() => onPage(pageNumber + 1)}
          >
            <ChevronRight size={18} />
          </button>
        </div>
      )}
      {isSelling && (
        <p className="subtle-note">
          Показаны твои объявления на выбранной странице.
        </p>
      )}
    </section>
  );
}
