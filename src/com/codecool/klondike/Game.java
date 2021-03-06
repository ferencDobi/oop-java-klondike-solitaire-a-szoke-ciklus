package com.codecool.klondike;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;

import java.util.Iterator;
import java.util.List;

public class Game extends Pane {

    private List<Card> deck;

    private Pile stockPile;
    private Pile discardPile;
    private List<Pile> foundationPiles = FXCollections.observableArrayList();
    private List<Pile> tableauPiles = FXCollections.observableArrayList();

    private double dragStartX, dragStartY;
    private List<Card> draggedCards = FXCollections.observableArrayList();

    private static double STOCK_GAP = 1;
    private static double FOUNDATION_GAP = 0;
    private static double TABLEAU_GAP = 30;


    private EventHandler<MouseEvent> onMouseClickedHandler = e -> {
        Card card = (Card) e.getSource();
        Pile.PileType pileType = card.getContainingPile().getPileType();
        if (pileType == Pile.PileType.STOCK) {
            card = stockPile.getTopCard();
            card.moveToPile(discardPile);
            card.flip();
            card.setMouseTransparent(false);
            System.out.println("Placed " + card + " to the waste.");
        } else if ((pileType != Pile.PileType.FOUNDATION && !card.isFaceDown()) && e.getClickCount() == 2) {
            for (Pile pile : foundationPiles) {
                if (isMoveValid(card, pile)) {
                    card.moveToPile(pile);
                    handleValidMove(card, pile);
                    break;
                }
            }

        }
    };

    private EventHandler<MouseEvent> stockReverseCardsHandler = e -> {
        if (stockPile.isEmpty()) refillStockFromDiscard();
    };

    private EventHandler<MouseEvent> onMousePressedHandler = e -> {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
    };

    private EventHandler<MouseEvent> onMouseDraggedHandler = e -> {
        Card card = (Card) e.getSource();
        Pile activePile = card.getContainingPile();
        if (activePile.getPileType() == Pile.PileType.STOCK || card.isFaceDown()) {
            return;
        }
        double offsetX = e.getSceneX() - dragStartX;
        double offsetY = e.getSceneY() - dragStartY;

        draggedCards.clear();
        List<Card> cards = activePile.getCards();
        List<Card> cardsToDrag = cards.subList(cards.indexOf(card), cards.size());
        draggedCards.addAll(cardsToDrag);

        draggedCards.forEach(c -> c.getDropShadow().setRadius(20));
        draggedCards.forEach(c -> c.getDropShadow().setOffsetX(10));
        draggedCards.forEach(c -> c.getDropShadow().setOffsetY(10));

        draggedCards.forEach(Card::toFront);
        draggedCards.forEach(c -> c.setTranslateX(offsetX));
        draggedCards.forEach(c -> c.setTranslateY(offsetY));
    };

    private EventHandler<MouseEvent> onMouseReleasedHandler = e -> {
        if (draggedCards.isEmpty())
            return;
        Card card = (Card) e.getSource();
        Pile pile = getValidIntersectingPile(card, tableauPiles);
        if (pile != null && isMoveValid(card, pile)) {
            handleValidMove(card, pile);
        } else {
            pile = getValidIntersectingPile(card, foundationPiles);
            if (pile != null && isMoveValid(card, pile)) {
                handleValidMove(card, pile);
            } else {
                draggedCards.forEach(MouseUtil::slideBack);
                draggedCards.clear();
            }
        }
    };

    private boolean isGameWon() {
        int foundationCount = 0;
        for (Pile pile : foundationPiles) {
            foundationCount += pile.numOfCards();
        }

        return foundationCount == 52; //standard 52-card deck
    }

    public Game() {
        deck = Card.createNewDeck();
        initPiles();
        dealCards();
    }

    private void addMouseEventHandlers(Card card) {
        card.setOnMousePressed(onMousePressedHandler);
        card.setOnMouseDragged(onMouseDraggedHandler);
        card.setOnMouseReleased(onMouseReleasedHandler);
        card.setOnMouseClicked(onMouseClickedHandler);
    }

    private void refillStockFromDiscard() {
        ObservableList<Card> discardedCards = discardPile.getCards();
        FXCollections.reverse(discardedCards);
        for (Card card : discardedCards) {
            card.flip();
            stockPile.addCard(card);
        }
        discardPile.clear();
        System.out.println("Stock refilled from discard pile.");
    }

    private boolean isMoveValid(Card card, Pile destPile) {
        Card topCard;
        if (destPile.getPileType() == Pile.PileType.FOUNDATION) {
            if (destPile.isEmpty()) return card.getRank() == 1;
            topCard = destPile.getTopCard();
            return Card.isSameSuit(card, topCard) && card.getRank() == topCard.getRank() + 1;
        } else if (destPile.getPileType() == Pile.PileType.TABLEAU) {
            if (destPile.isEmpty()) return card.getRank() == 13;
            topCard = destPile.getTopCard();
            return Card.isOppositeColor(card, topCard) && card.getRank() == topCard.getRank() - 1;
        }
        return false;
    }

    private Pile getValidIntersectingPile(Card card, List<Pile> piles) {
        Pile result = null;
        for (Pile pile : piles) {
            if (!pile.equals(card.getContainingPile()) &&
                    isOverPile(card, pile) &&
                    isMoveValid(card, pile))
                result = pile;
        }
        return result;
    }

    private boolean isOverPile(Card card, Pile pile) {
        if (pile.isEmpty())
            return card.getBoundsInParent().intersects(pile.getBoundsInParent());
        else
            return card.getBoundsInParent().intersects(pile.getTopCard().getBoundsInParent());
    }

    private void handleValidMove(Card card, Pile destPile) {
        String msg = null;
        if (destPile.isEmpty()) {
            if (destPile.getPileType().equals(Pile.PileType.FOUNDATION))
                msg = String.format("Placed %s to the foundation.", card);
            if (destPile.getPileType().equals(Pile.PileType.TABLEAU))
                msg = String.format("Placed %s to a new pile.", card);
        } else {
            msg = String.format("Placed %s to %s.", card, destPile.getTopCard());
        }
        System.out.println(msg);
        MouseUtil.slideToDest(draggedCards, destPile);
        draggedCards.clear();
        if (isGameWon()) handleWinningGame();
    }

    private void handleWinningGame() {
        int modalWidth = 426, modalHeight = 100;
        Interaction.showModal("You win!", "Congratulations, you have won!", modalWidth, modalHeight);
        Interaction.addQuitGameBtn();
        Interaction.addNewGameBtn();
    }


    private void initPiles() {
        stockPile = new Pile(Pile.PileType.STOCK, "Stock", STOCK_GAP);
        stockPile.setBlurredBackground();
        stockPile.setLayoutX(35);
        stockPile.setLayoutY(40);
        stockPile.setOnMouseClicked(stockReverseCardsHandler);
        getChildren().add(stockPile);

        discardPile = new Pile(Pile.PileType.DISCARD, "Discard", STOCK_GAP);
        discardPile.setBlurredBackground();
        discardPile.setLayoutX(211);
        discardPile.setLayoutY(40);
        getChildren().add(discardPile);

        for (int i = 0; i < 4; i++) {
            Pile foundationPile = new Pile(Pile.PileType.FOUNDATION, "Foundation " + i, FOUNDATION_GAP);
            foundationPile.setBlurredBackground();
            foundationPile.setLayoutX(563 + i * 176);
            foundationPile.setLayoutY(40);
            foundationPiles.add(foundationPile);
            getChildren().add(foundationPile);
        }
        for (int i = 0; i < 7; i++) {
            Pile tableauPile = new Pile(Pile.PileType.TABLEAU, "Tableau " + i, TABLEAU_GAP);
            tableauPile.setBlurredBackground();
            tableauPile.setLayoutX(35 + i * 176);
            tableauPile.setLayoutY(295);
            tableauPiles.add(tableauPile);
            getChildren().add(tableauPile);
        }
    }

    private void dealCards() {
        Iterator<Card> deckIterator = deck.iterator();
        int count = 1;
        for (Pile tableauPile : tableauPiles) {
            for (int i = 0; i < count; i++) {
                Card card = deckIterator.next();
                tableauPile.addCard(card);
                addMouseEventHandlers(card);
                getChildren().add(card);
                if (i == count - 1) card.flip();
            }
            count++;
        }
        deckIterator.forEachRemaining(card -> {
            stockPile.addCard(card);
            addMouseEventHandlers(card);
            getChildren().add(card);
        });

    }

    public void setTableBackground(Image tableBackground) {
        setBackground(new Background(new BackgroundImage(tableBackground,
                BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));
    }

    public List<Card> getDeck() {
        return deck;
    }

}
